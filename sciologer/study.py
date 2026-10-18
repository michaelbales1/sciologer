"""Batch studies: run a list of PubMed queries with identical settings and compare them.

A study is a YAML file:

    name: Editorial policy and collaboration, 2021-2026
    settings:
      types: [author, institution]
      views: [entities, text, citations]   # add geography for the world map
      max_records: all          # or a number; "all" splits >10,000 by date
      sample: 2000              # optional: random sample when a set is larger
    queries:
      - {label: JBI, query: '"J Biomed Inform"[TA] AND 2021:2026[DP]'}
      - '"J Am Med Inform Assoc"[TA] AND 2021:2026[DP]'   # label = the query

Running it (`sciologer batch study.yaml`) fetches each query, builds its maps (saved as
ordinary runs), computes collaboration measures on its complete co-authorship network
(sciologer.metrics), and writes a comparison table. Progress is saved after every
query, so an interrupted batch resumes where it stopped.

Each query's PMIDs are frozen on first retrieval (pmids/<label>.txt). Later runs reuse
that exact set, so results reproduce even as PubMed adds papers; `--refresh` searches
PubMed again. Every setting, count and retrieval date is recorded in the study folder.
"""

from __future__ import annotations

import csv
import json
import random
import re
import traceback
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import Literal

import yaml
from pydantic import BaseModel, Field, field_validator, model_validator

from .citations import ICiteClient
from .geo import RORClient
from .metrics import METRIC_INFO, MetricParams, collaboration_metrics
from .parse import parse_batches
from .pipeline import DEFAULT_TYPES, RunParams, run_pipeline
from .pubmed import PubMedClient, SearchResult
from .records import EntityType
from .runs import RunStore, new_run_id

View = Literal["entities", "text", "citations", "geography"]
# Sets bigger than this need an explicit sample size or allow_large: true.
LARGE_SET = 20_000


class StudySettings(BaseModel):
    types: list[EntityType] = Field(default_factory=lambda: list(DEFAULT_TYPES))
    views: list[View] = Field(default_factory=lambda: ["entities", "text", "citations"])
    max_records: int | Literal["all"] = "all"
    sample: int | None = Field(None, ge=10)
    sample_seed: int = 42
    allow_large: bool = False
    # Anything else RunParams accepts (network, cluster, layout, semantic, citations…).
    params: dict = Field(default_factory=dict)
    metrics: MetricParams = Field(default_factory=MetricParams)


class StudyQuery(BaseModel):
    label: str
    query: str

    @field_validator("label")
    @classmethod
    def _label(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("label can't be empty")
        return v


class Study(BaseModel):
    name: str
    description: str | None = None
    settings: StudySettings = Field(default_factory=StudySettings)
    queries: list[StudyQuery]

    @model_validator(mode="before")
    @classmethod
    def _plain_queries(cls, data: dict) -> dict:
        # Allow plain strings in the query list; the query doubles as its label.
        qs = data.get("queries") or []
        data["queries"] = [{"label": q, "query": q} if isinstance(q, str) else q for q in qs]
        return data

    @model_validator(mode="after")
    def _unique_labels(self) -> Study:
        labels = [q.label for q in self.queries]
        dupes = sorted({x for x in labels if labels.count(x) > 1})
        if dupes:
            raise ValueError(f"query labels must be unique; repeated: {', '.join(dupes)}")
        if not self.queries:
            raise ValueError("a study needs at least one query")
        return self

    @property
    def id(self) -> str:
        return slug(self.name)


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:60] or "study"


def load_study(path: Path) -> Study:
    data = yaml.safe_load(Path(path).read_text())
    if not isinstance(data, dict):
        raise ValueError(f"{path} isn't a study file (expected name, settings, queries)")
    return Study.model_validate(data)


class StudyStore:
    """~/.sciologer/studies/<study-id>/: study.yaml, status.json, pmids/, metrics.csv."""

    def __init__(self, runs: RunStore) -> None:
        self.runs = runs
        self.dir = runs.home / "studies"

    def path(self, study_id: str) -> Path:
        if not re.fullmatch(r"[a-z0-9-]{1,60}", study_id):
            raise KeyError(study_id)
        return self.dir / study_id

    def status(self, study_id: str) -> dict:
        f = self.path(study_id) / "status.json"
        if not f.exists():
            raise KeyError(study_id)
        return json.loads(f.read_text())

    def list(self) -> list[dict]:
        if not self.dir.exists():
            return []
        out = []
        for d in sorted(self.dir.iterdir()):
            if (d / "status.json").exists():
                st = json.loads((d / "status.json").read_text())
                qs = st["queries"]
                out.append(
                    {
                        "id": d.name,
                        "name": st["name"],
                        "queries": len(qs),
                        "done": sum(1 for q in qs if q["state"] == "done"),
                        "failed": sum(1 for q in qs if q["state"] == "error"),
                        "updated_at": st.get("updated_at"),
                    }
                )
        return out


def _write_metrics_csv(folder: Path, status: dict) -> None:
    names = list(METRIC_INFO)
    with (folder / "metrics.csv").open("w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["label", "query", "state", "run_id", "pubmed_matches", "retrieved", *names])
        for q in status["queries"]:
            m = q.get("metrics") or {}
            w.writerow(
                [
                    q["label"],
                    q["query"],
                    q["state"],
                    q.get("run_id") or "",
                    q.get("pubmed_count", ""),
                    q.get("retrieved", ""),
                    *[("" if m.get(n) is None else m[n]) for n in names],
                ]
            )


def run_study(
    study: Study,
    store: StudyStore,
    source_text: str | None = None,
    refresh: bool = False,
    only: list[str] | None = None,
    pubmed: PubMedClient | None = None,
    icite: ICiteClient | None = None,
    log: Callable[[str], None] = print,
) -> dict:
    """Run every pending query of a study; returns the final status."""
    folder = store.path(study.id)
    (folder / "pmids").mkdir(parents=True, exist_ok=True)
    if source_text is not None:
        (folder / "study.yaml").write_text(source_text)
    status_file = folder / "status.json"
    previous = json.loads(status_file.read_text()) if status_file.exists() else {"queries": []}
    by_label = {q["label"]: q for q in previous["queries"]}
    status = {
        "id": study.id,
        "name": study.name,
        "description": study.description,
        "settings": study.settings.model_dump(mode="json"),
        "metric_info": {k: {"label": a, "description": b} for k, (a, b) in METRIC_INFO.items()},
        "queries": [
            by_label.get(q.label, {"label": q.label, "query": q.query, "state": "pending"})
            | {"query": q.query}
            for q in study.queries
        ],
    }

    def save() -> None:
        status["updated_at"] = datetime.now(UTC).isoformat()
        status_file.write_text(json.dumps(status, indent=2))
        _write_metrics_csv(folder, status)

    own_pubmed, own_icite = pubmed is None, icite is None
    pubmed = pubmed or PubMedClient(cache_dir=store.runs.cache_dir)
    icite = icite or ICiteClient(cache_dir=store.runs.cache_dir)
    ror = RORClient(cache_dir=store.runs.cache_dir)
    s = study.settings
    try:
        for i, entry in enumerate(status["queries"], 1):
            label = entry["label"]
            if only and label not in only:
                continue
            if entry["state"] == "done" and not refresh:
                log(f"[{i}/{len(status['queries'])}] {label}: done earlier, skipping")
                continue
            log(f"[{i}/{len(status['queries'])}] {label}: {entry['query']}")
            entry.update(state="running", error=None, started_at=datetime.now(UTC).isoformat())
            save()
            try:
                sr = _retrieve(entry, folder, s, pubmed, refresh, log)
                params = RunParams.model_validate(
                    {
                        **s.params,
                        "query": entry["query"],
                        "types": [t.value for t in s.types],
                        "text_map": "text" in s.views,
                        "citation_map": "citations" in s.views,
                        "geo_map": "geography" in s.views,
                        "retrieve_all": s.max_records == "all",
                    }
                )
                run_id = new_run_id(f"{study.id}-{label}")
                out = run_pipeline(
                    params, run_id, search_result=sr, icite_client=icite, ror_client=ror
                )
                out.manifest.study = {"id": study.id, "name": study.name, "label": label}
                store.runs.save(out)
                papers = parse_batches(sr.xml_batches)
                entry.update(
                    state="done",
                    run_id=run_id,
                    metrics=collaboration_metrics(papers, s.metrics),
                    warnings=out.manifest.warnings,
                    finished_at=datetime.now(UTC).isoformat(),
                )
                log(f"    done: {len(papers):,} papers, run {run_id}")
            except Exception as exc:  # noqa: BLE001 - recorded; the batch continues
                entry.update(state="error", error=str(exc) or type(exc).__name__)
                log(f"    failed: {entry['error']}")
                traceback.print_exc()
            save()
    finally:
        if own_pubmed:
            pubmed.close()
        if own_icite:
            icite.close()
        ror.close()
    save()
    return status


def _retrieve(
    entry: dict,
    folder: Path,
    s: StudySettings,
    pubmed: PubMedClient,
    refresh: bool,
    log: Callable[[str], None],
) -> SearchResult:
    """Frozen PMIDs if present; otherwise search, sample if needed, and freeze."""
    frozen = folder / "pmids" / f"{slug(entry['label'])}.txt"
    if frozen.exists() and not refresh:
        pmids = frozen.read_text().split()
        log(f"    reusing {len(pmids):,} frozen PMIDs")
        sr = pubmed.fetch_ids(pmids)
        sr.count = entry.get("pubmed_count", len(pmids))
        sr.sampled_from = entry.get("sampled_from")
        entry["retrieved"] = len(pmids)
        return sr

    if s.max_records == "all":
        count, _ = pubmed.count(entry["query"])
        entry["pubmed_count"] = count
        entry["retrieved_at"] = datetime.now(UTC).isoformat()
        if s.sample and count > s.sample:
            ids, _, _ = pubmed.ids_all(entry["query"])
            rng = random.Random(f"{s.sample_seed}:{entry['label']}")
            pmids = sorted(rng.sample(ids, s.sample), key=int)
            entry["sampled_from"] = count
            log(f"    {count:,} matches; random sample of {len(pmids):,}")
            sr = pubmed.fetch_ids(pmids)
            sr.count = count
            sr.sampled_from = count
        elif count > LARGE_SET and not s.allow_large:
            raise ValueError(
                f"{count:,} matching papers. For sets over {LARGE_SET:,}, add "
                "'sample: <size>' or 'allow_large: true' to the study settings."
            )
        else:
            log(f"    {count:,} matches; fetching all")
            sr = pubmed.search_all(entry["query"])
            entry.pop("sampled_from", None)
    else:
        sr = pubmed.search(entry["query"], max_records=s.max_records)
        entry["pubmed_count"] = sr.count
        entry["retrieved_at"] = sr.retrieved_at
    frozen.write_text("\n".join(sr.pmids) + "\n")
    entry["retrieved"] = len(sr.pmids)
    return sr
