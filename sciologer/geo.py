"""The geographic view: institutions from author affiliations, placed on a world map.

Each distinct affiliation string is matched against the Research Organization Registry
(ROR, https://ror.org) with its affiliation-matching service, which returns a
"chosen" result only when it is confident. Only chosen matches are placed; anything
else is counted as unplaced and listed, not guessed. In tests on PubMed affiliations,
about 85-90% of distinct strings got a chosen match. Full strings match better than
shortened ones (35 vs 31 of 40), so they are sent whole, minus emails and URLs.

Matching by registry ID merges the many spellings of one institution ("Columbia Univ",
"Columbia University Irving Medical Center"... resolve to whatever ROR picks), and gives
each institution a location: ROR records the city, so institutions in the same city
share a point and the map spreads them around it.

Two institutions are linked when they appear together on a paper; link weight is the
number of shared papers. Node size is the number of papers.

ROR allows 2,000 requests per 5 minutes per IP address without registration, and has
announced a lower limit (50 per 5 minutes) for unregistered clients later. Requests are
spaced across threads, honor Retry-After, and every answer (including "no match") is
cached on disk, so a repeated or overlapping query costs nothing. Set ROR_CLIENT_ID to
send a registered client ID.
"""

from __future__ import annotations

import json
import os
import random
import re
import threading
import time
from collections import Counter, defaultdict
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from itertools import combinations
from pathlib import Path

import httpx
from pydantic import BaseModel, Field

from .citations import _Limiter
from .countries import country_of
from .provenance import Step
from .records import Author, Paper

ROR = "https://api.ror.org/v2/organizations"
# Measured: about 5.5 lookups per second with 4 threads under the shared limiter.
LOOKUPS_PER_SECOND = 5.5
# 2,000 per 5 minutes is 6.7 per second; stay a little under it.
_LIMITER = _Limiter(0.17)
_CONTACT = re.compile(r"electronic address:?.*$|\S+@\S+|https?://\S+", re.I)


def clean_affiliation(text: str) -> str:
    """The affiliation without contact details, whitespace collapsed (also the cache key)."""
    return " ".join(_CONTACT.sub("", text).split()).strip(" .;,")


class GeoParams(BaseModel):
    # Distinct affiliations looked up per map, most frequent first. The rest are
    # counted as unplaced (a warning says how many).
    max_lookups: int = Field(2500, ge=0, le=50_000)
    # Institutions drawn: the most productive ones; the others are counted.
    max_institutions: int = Field(300, ge=10, le=5000)
    threads: int = Field(4, ge=1, le=8)


class RORError(RuntimeError):
    pass


class RORClient:
    """ROR affiliation matching with a persistent cache of every answer."""

    def __init__(
        self,
        cache_dir: Path | None = None,
        transport: httpx.BaseTransport | None = None,
        sleep: Callable[[float], None] = time.sleep,
        client_id: str | None = None,
    ) -> None:
        self._sleep = sleep
        client_id = client_id if client_id is not None else os.environ.get("ROR_CLIENT_ID")
        headers = {"Client-Id": client_id} if client_id else {}
        self._http = httpx.Client(timeout=30, transport=transport, headers=headers)
        self._path = cache_dir / "ror-affiliations.json" if cache_dir else None
        self._lock = threading.Lock()
        self._cache: dict[str, dict | None] = {}
        if self._path and self._path.exists():
            self._cache = json.loads(self._path.read_text())
        self.retries = 0
        self.looked_up = 0
        self.from_cache = 0

    def _request(self, affiliation: str) -> list[dict]:
        for attempt in range(6):
            _LIMITER.wait(self._sleep)
            try:
                resp = self._http.get(ROR, params={"affiliation": affiliation})
            except httpx.TransportError:
                if attempt == 5:
                    raise
                self.retries += 1
                _LIMITER.penalize(min(30.0, 2**attempt) + random.uniform(0, 0.5))
                continue
            if resp.status_code == 429 or resp.status_code >= 500:
                self.retries += 1
                retry = resp.headers.get("Retry-After")
                delay = float(retry) if retry and retry.isdigit() else min(60.0, 2 ** (attempt + 1))
                _LIMITER.penalize(delay + random.uniform(0, 0.5))
                continue
            resp.raise_for_status()
            return resp.json().get("items", [])
        raise RORError("ROR kept refusing requests. Wait a few minutes and try again.")

    @staticmethod
    def _match(items: list[dict]) -> dict | None:
        chosen = next((i for i in items if i.get("chosen")), None)
        if not chosen:
            return None
        org = chosen["organization"]
        loc = (org.get("locations") or [{}])[0].get("geonames_details") or {}
        if loc.get("lat") is None or loc.get("lng") is None:
            return None
        name = next(
            (n["value"] for n in org.get("names", []) if "ror_display" in n.get("types", [])),
            org.get("id"),
        )
        return {
            "ror": org["id"],
            "name": name,
            "city": loc.get("name"),
            "region": loc.get("country_subdivision_name"),
            "country": loc.get("country_code"),
            "country_name": loc.get("country_name"),
            "lat": loc["lat"],
            "lng": loc["lng"],
            "kinds": org.get("types", []),
            "score": chosen.get("score"),
        }

    def is_cached(self, affiliation: str) -> bool:
        with self._lock:
            return affiliation in self._cache

    def match(self, affiliation: str) -> dict | None:
        """The confident ROR match for one cleaned affiliation, or None."""
        with self._lock:
            if affiliation in self._cache:
                self.from_cache += 1
                return self._cache[affiliation]
        result = self._match(self._request(affiliation))
        with self._lock:
            self._cache[affiliation] = result
            self.looked_up += 1
        return result

    def match_all(
        self,
        affiliations: list[str],
        threads: int = 4,
        progress: Callable[[int, int], None] | None = None,
    ) -> dict[str, dict | None]:
        out: dict[str, dict | None] = {}
        done = 0
        try:
            with ThreadPoolExecutor(max_workers=threads) as pool:
                for aff, res in zip(affiliations, pool.map(self.match, affiliations), strict=True):
                    out[aff] = res
                    done += 1
                    if progress and (done % 25 == 0 or done == len(affiliations)):
                        progress(done, len(affiliations))
        finally:
            self.save()
        return out

    def save(self) -> None:
        if not self._path:
            return
        with self._lock:
            self._path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self._path.with_suffix(".tmp")
            tmp.write_text(json.dumps(self._cache))
            tmp.replace(self._path)

    def close(self) -> None:
        self._http.close()


def pack_affiliations(papers: list[Paper]) -> dict:
    """Each paper's author affiliations, with repeated strings stored once.

    Saved with every map so the geographic view can be added later without going back
    to PubMed.
    """
    index: dict[str, int] = {}
    out: dict[str, list[list[int]]] = {}
    for p in papers:
        out[p.pmid] = [
            [index.setdefault(aff, len(index)) for aff in a.affiliations] for a in p.authors
        ]
    return {"strings": list(index), "papers": out}


def unpack_affiliations(data: dict) -> list[Paper]:
    """Papers carrying only their authors' affiliations (enough for the geographic view)."""
    strings = data["strings"]
    return [
        Paper(pmid=pmid, authors=[Author(affiliations=[strings[i] for i in a]) for a in authors])
        for pmid, authors in data["papers"].items()
    ]


def _distinct_affiliations(papers: list[Paper]) -> tuple[Counter[str], int]:
    freq: Counter[str] = Counter()
    authorships = 0
    for p in papers:
        for a in p.authors:
            for raw in a.affiliations:
                aff = clean_affiliation(raw)
                if aff:
                    freq[aff] += 1
                    authorships += 1
    return freq, authorships


def estimate(papers: list[Paper], params: GeoParams, client: RORClient) -> dict:
    """How much ROR work the geographic view needs: lookups not yet cached, and time."""
    freq, _ = _distinct_affiliations(papers)
    planned = [a for a, _ in freq.most_common(params.max_lookups)]
    to_lookup = sum(1 for a in planned if not client.is_cached(a))
    return {
        "distinct_affiliations": len(freq),
        "planned": len(planned),
        "cached": len(planned) - to_lookup,
        "to_lookup": to_lookup,
        "seconds": round(to_lookup / LOOKUPS_PER_SECOND),
    }


def geo_step(summary: dict, params: GeoParams) -> tuple[Step, list[str]]:
    """The provenance step for the geographic view, and any warnings."""
    step = Step(
        name="Geography",
        detail=(
            "Each distinct affiliation (without emails or URLs) was matched with the Research "
            "Organization Registry's affiliation service (ROR, ror.org); only matches ROR "
            "marks as chosen (confident) are placed, at the city ROR records. Most frequent "
            f"affiliations first, up to {params.max_lookups:,}. Institutions are linked when "
            f"they share a paper; the {params.max_institutions} with the most papers are drawn."
        ),
        counts=summary,
    )
    warnings = []
    if summary["not_looked_up"]:
        warnings.append(
            f"Geographic view: {summary['not_looked_up']:,} rarer affiliations weren't looked "
            f"up (limit {params.max_lookups:,} per map)."
        )
    return step, warnings


def build_geo_map(
    papers: list[Paper],
    params: GeoParams,
    client: RORClient,
    progress: Callable[[str, float], None] | None = None,
) -> tuple[dict | None, dict]:
    """The geographic view's data and a summary for the provenance record.

    `progress` gets a message and the share of lookups done (0–1).
    """
    report = progress or (lambda _m, _f: None)
    # Distinct affiliations by how many authorships list them (most frequent first).
    freq, authorships = _distinct_affiliations(papers)
    if not freq:
        return None, {"skipped": "No author affiliations in these records"}
    ranked = [a for a, _ in freq.most_common()]
    to_match = ranked[: params.max_lookups]
    started = time.monotonic()
    matches = client.match_all(
        to_match,
        threads=params.threads,
        progress=lambda d, n: report(f"Locating institutions with ROR: {d:,} of {n:,}", d / n),
    )

    # Institutions (by ROR ID) with their papers and the strings that matched them.
    inst: dict[str, dict] = {}
    pmids_of: dict[str, set[str]] = defaultdict(set)
    spellings: dict[str, Counter[str]] = defaultdict(Counter)
    paper_insts: dict[str, set[str]] = {}
    placed_authorships = 0
    unplaced: Counter[str] = Counter()
    unplaced_countries: Counter[str] = Counter()
    papers_located = 0
    for p in papers:
        here: set[str] = set()
        for a in p.authors:
            for raw in a.affiliations:
                aff = clean_affiliation(raw)
                if not aff:
                    continue
                m = matches.get(aff)
                if m:
                    inst.setdefault(m["ror"], m)
                    pmids_of[m["ror"]].add(p.pmid)
                    spellings[m["ror"]][aff] += 1
                    here.add(m["ror"])
                    placed_authorships += 1
                else:
                    unplaced[aff] += 1
                    unplaced_countries[country_of(aff) or "unknown"] += 1
        paper_insts[p.pmid] = here
        papers_located += bool(here)

    ordered = sorted(inst, key=lambda r: (-len(pmids_of[r]), inst[r]["name"]))
    shown = ordered[: params.max_institutions]
    shown_set = set(shown)
    links: Counter[tuple[str, str]] = Counter()
    for here in paper_insts.values():
        for a, b in combinations(sorted(here & shown_set), 2):
            links[(a, b)] += 1

    countries: dict[str, dict] = {}
    for r in ordered:
        m = inst[r]
        c = countries.setdefault(
            m["country"] or "?",
            {"code": m["country"], "name": m["country_name"], "institutions": 0, "pmids": set()},
        )
        c["institutions"] += 1
        c["pmids"] |= pmids_of[r]

    nodes = [
        {
            "id": r,
            "label": inst[r]["name"],
            "city": inst[r]["city"],
            "region": inst[r]["region"],
            "country": inst[r]["country"],
            "country_name": inst[r]["country_name"],
            "lat": inst[r]["lat"],
            "lng": inst[r]["lng"],
            "kinds": inst[r]["kinds"],
            "pmids": sorted(pmids_of[r], key=int),
            "count": len(pmids_of[r]),
            "variants": [s for s, _ in spellings[r].most_common(12)],
        }
        for r in shown
    ]
    data = {
        "institutions": nodes,
        "edges": [
            {"source": a, "target": b, "count": n, "weight": n}
            for (a, b), n in sorted(links.items(), key=lambda kv: -kv[1])
        ],
        "countries": sorted(
            (
                {**{k: v for k, v in c.items() if k != "pmids"}, "papers": len(c["pmids"])}
                for c in countries.values()
            ),
            key=lambda c: -c["papers"],
        ),
        "unplaced": [{"affiliation": a, "count": n} for a, n in unplaced.most_common(40)],
        "unplaced_countries": dict(unplaced_countries.most_common()),
    }
    summary = {
        "papers": len(papers),
        "papers_located": papers_located,
        "authorships_with_affiliation": authorships,
        "authorships_placed": placed_authorships,
        "distinct_affiliations": len(freq),
        "looked_up": len(to_match),
        "not_looked_up": len(ranked) - len(to_match),
        "matched_affiliations": sum(1 for v in matches.values() if v),
        "institutions": len(inst),
        "institutions_shown": len(shown),
        "countries": len(countries),
        "links": len(links),
        "ror_requests": client.looked_up,
        "ror_from_cache": client.from_cache,
        "ror_retries": client.retries,
        "seconds": round(time.monotonic() - started, 1),
        "retrieved_at": datetime.now(UTC).isoformat(),
    }
    return data, summary
