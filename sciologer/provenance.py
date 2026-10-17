"""Run manifest: a record of every choice that shaped a map."""

from __future__ import annotations

import contextlib
import platform
from datetime import UTC, datetime
from importlib.metadata import PackageNotFoundError, version

from pydantic import BaseModel, Field

from . import __version__
from .normalize import LogEntry

_PACKAGES = [
    "networkx",
    "numpy",
    "lxml",
    "httpx",
    "pydantic",
    "fastapi",
    "scikit-learn",
    "model2vec",
]


def software_versions() -> dict[str, str]:
    out = {"sciologer": __version__, "python": platform.python_version()}
    for pkg in _PACKAGES:
        with contextlib.suppress(PackageNotFoundError):
            out[pkg] = version(pkg)
    return out


class Step(BaseModel):
    name: str
    detail: str
    counts: dict = Field(default_factory=dict)


class Manifest(BaseModel):
    run_id: str
    created_at: str = Field(default_factory=lambda: datetime.now(UTC).isoformat())
    software: dict[str, str] = Field(default_factory=software_versions)
    query: str
    query_translation: str | None = None
    source: str = "PubMed via NCBI E-utilities (esearch + efetch, XML); citations from NIH iCite"
    pubmed_count: int = 0
    fetched: int = 0
    parsed: int = 0
    from_cache: bool = False
    retrieved_at: str | None = None  # when PubMed was queried (earlier than created_at if cached)
    pmids: list[str] = Field(default_factory=list)
    parameters: dict = Field(default_factory=dict)
    steps: list[Step] = Field(default_factory=list)
    normalization: list[LogEntry] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
