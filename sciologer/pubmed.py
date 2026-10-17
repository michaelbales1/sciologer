"""PubMed retrieval through NCBI E-utilities.

Uses the history server (esearch with usehistory=y, then efetch in batches), as the
2008 getPubs did, but asks for XML instead of MEDLINE text so fields parse reliably.

NCBI rate-limits E-utilities: 3 requests/second without an API key, 10 with one, and
it blocks clients that exceed this. So every request goes through one process-wide
limiter (shared by concurrent jobs), 429/5xx responses back off with jitter and honor
Retry-After, and responses are cached on disk so repeating a query costs nothing.
"""

from __future__ import annotations

import hashlib
import json
import os
import random
import threading
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import httpx
from lxml import etree

EUTILS = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
TOOL = "sciologer"
BATCH_SIZE = 200
MAX_ATTEMPTS = 6


class RateLimitError(RuntimeError):
    """PubMed kept refusing requests after all retries."""


class _Limiter:
    """Spaces requests at least `interval` seconds apart across all threads."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._next = 0.0

    def wait(self, interval: float, sleep: Callable[[float], None] = time.sleep) -> None:
        with self._lock:
            now = time.monotonic()
            start = max(now, self._next)
            self._next = start + interval
        if start > now:
            sleep(start - now)

    def reset(self) -> None:
        with self._lock:
            self._next = 0.0

    def penalize(self, seconds: float) -> None:
        """Push back every pending request, e.g. after a 429."""
        with self._lock:
            self._next = max(self._next, time.monotonic() + seconds)


_LIMITER = _Limiter()


def _retry_after(resp: httpx.Response) -> float | None:
    value = resp.headers.get("Retry-After")
    try:
        return float(value) if value else None
    except ValueError:
        return None


@dataclass
class SearchResult:
    query: str
    count: int
    pmids: list[str]
    query_translation: str | None
    xml_batches: list[bytes] = field(default_factory=list)
    from_cache: bool = False
    retries: int = 0
    retrieved_at: str | None = None


class PubMedClient:
    """Polite E-utilities client. Set NCBI_API_KEY and NCBI_EMAIL to identify yourself."""

    def __init__(
        self,
        api_key: str | None = None,
        email: str | None = None,
        cache_dir: Path | None = None,
        transport: httpx.BaseTransport | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.api_key = api_key if api_key is not None else os.environ.get("NCBI_API_KEY")
        self.email = email if email is not None else os.environ.get("NCBI_EMAIL")
        # Stay a little under NCBI's published limits.
        self.interval = 0.11 if self.api_key else 0.35
        self.cache_dir = cache_dir
        self._sleep = sleep
        self._http = httpx.Client(timeout=60, transport=transport)
        self.retries = 0

    def _params(self, **extra: str | int) -> dict[str, str | int]:
        params: dict[str, str | int] = {"db": "pubmed", "tool": TOOL, **extra}
        if self.api_key:
            params["api_key"] = self.api_key
        if self.email:
            params["email"] = self.email
        return params

    def _post(self, endpoint: str, **params: str | int) -> bytes:
        for attempt in range(MAX_ATTEMPTS):
            _LIMITER.wait(self.interval, self._sleep)
            try:
                resp = self._http.post(f"{EUTILS}/{endpoint}", data=self._params(**params))
            except httpx.TransportError:
                if attempt == MAX_ATTEMPTS - 1:
                    raise
                self.retries += 1
                self._sleep(min(30.0, 2**attempt) + random.uniform(0, 0.5))
                continue
            if resp.status_code == 429 or resp.status_code >= 500:
                if attempt == MAX_ATTEMPTS - 1:
                    break
                self.retries += 1
                # Hold back every thread, not just this one; the next wait() sleeps.
                _LIMITER.penalize(
                    _retry_after(resp) or min(30.0, 2**attempt) + random.uniform(0, 0.5)
                )
                continue
            resp.raise_for_status()
            return resp.content
        raise RateLimitError(
            f"PubMed refused {endpoint} after {MAX_ATTEMPTS} attempts "
            f"(HTTP {resp.status_code}). Wait a minute and try again, or set NCBI_API_KEY."
        )

    # --- cache ---------------------------------------------------------------------

    def _cache_path(self, query: str, max_records: int) -> Path | None:
        if self.cache_dir is None:
            return None
        digest = hashlib.sha256(f"{query}\0{max_records}".encode()).hexdigest()[:16]
        return self.cache_dir / digest

    def _load_cache(self, path: Path | None) -> SearchResult | None:
        if path is None or not (path / "meta.json").exists():
            return None
        meta = json.loads((path / "meta.json").read_text())
        batches = [(path / f"batch{i}.xml").read_bytes() for i in range(meta.pop("batches"))]
        return SearchResult(**meta, xml_batches=batches, from_cache=True)

    def _save_cache(self, path: Path | None, result: SearchResult) -> None:
        if path is None:
            return
        path.mkdir(parents=True, exist_ok=True)
        for i, batch in enumerate(result.xml_batches):
            (path / f"batch{i}.xml").write_bytes(batch)
        meta = {
            "query": result.query,
            "count": result.count,
            "pmids": result.pmids,
            "query_translation": result.query_translation,
            "retrieved_at": result.retrieved_at,
            "batches": len(result.xml_batches),
        }
        (path / "meta.json").write_text(json.dumps(meta))

    # --- public API ----------------------------------------------------------------

    def search(
        self,
        query: str,
        max_records: int = 1000,
        progress: Callable[[int, int], None] | None = None,
        use_cache: bool = True,
    ) -> SearchResult:
        """Run a PubMed query and fetch up to max_records full records as XML batches."""
        cache_path = self._cache_path(query, max_records)
        if use_cache and (cached := self._load_cache(cache_path)):
            return cached

        start_retries = self.retries
        root = etree.fromstring(
            self._post("esearch.fcgi", term=query, usehistory="y", retmax=max_records)
        )
        error = root.findtext("ERROR")
        if error:
            raise ValueError(f"PubMed rejected the query: {error}")
        result = SearchResult(
            query=query,
            retrieved_at=datetime.now(UTC).isoformat(),
            count=int(root.findtext("Count") or 0),
            pmids=[e.text for e in root.findall("IdList/Id") if e.text],
            query_translation=root.findtext("QueryTranslation"),
        )
        web_env = root.findtext("WebEnv") or ""
        query_key = root.findtext("QueryKey") or "1"
        total = len(result.pmids)
        for start in range(0, total, BATCH_SIZE):
            result.xml_batches.append(
                self._post(
                    "efetch.fcgi",
                    WebEnv=web_env,
                    query_key=query_key,
                    retstart=start,
                    retmax=min(BATCH_SIZE, total - start),
                    retmode="xml",
                )
            )
            if progress:
                progress(min(start + BATCH_SIZE, total), total)
        result.retries = self.retries - start_retries
        self._save_cache(cache_path, result)
        return result

    def close(self) -> None:
        self._http.close()
