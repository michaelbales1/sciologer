"""Run storage and background jobs.

Each run lives in its own folder under ~/.sciologer/runs/<run_id>/ (set SCIOLOGER_HOME
to move it), replacing the 2008 version's fixed c:\\e\\ tree. Raw PubMed responses are
cached in ~/.sciologer/cache/ so re-running a query doesn't hit the API again.
"""

from __future__ import annotations

import json
import os
import re
import threading
import traceback
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

from pydantic import BaseModel

from .pipeline import RunOutput, RunParams, run_pipeline
from .pubmed import PubMedClient

_RUN_ID = re.compile(r"^[0-9]{8}-[0-9]{6}-[a-z0-9-]{1,48}$")


def default_home() -> Path:
    return Path(os.environ.get("SCIOLOGER_HOME", Path.home() / ".sciologer"))


def new_run_id(query: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", query.lower()).strip("-")[:40].strip("-") or "query"
    return f"{datetime.now():%Y%m%d-%H%M%S}-{slug}"


class RunSummary(BaseModel):
    run_id: str
    query: str
    created_at: str
    papers: int
    nodes: int
    edges: int


class RunStore:
    def __init__(self, home: Path | None = None) -> None:
        self.home = home or default_home()
        self.runs_dir = self.home / "runs"
        self.cache_dir = self.home / "cache"
        self.runs_dir.mkdir(parents=True, exist_ok=True)

    def path(self, run_id: str) -> Path:
        if not _RUN_ID.match(run_id):
            raise KeyError(run_id)
        return self.runs_dir / run_id

    def save(self, out: RunOutput) -> Path:
        d = self.path(out.manifest.run_id)
        d.mkdir(parents=True, exist_ok=True)
        (d / "manifest.json").write_text(out.manifest.model_dump_json(indent=2))
        (d / "graph.json").write_text(json.dumps(out.graph))
        (d / "papers.json").write_text(json.dumps(out.papers))
        return d

    def load(self, run_id: str, name: str) -> dict:
        f = self.path(run_id) / f"{name}.json"
        if not f.exists():
            raise KeyError(run_id)
        return json.loads(f.read_text())

    def list(self) -> list[RunSummary]:
        out = []
        for d in sorted(self.runs_dir.iterdir(), reverse=True):
            if not (d / "manifest.json").exists() or not _RUN_ID.match(d.name):
                continue
            m = json.loads((d / "manifest.json").read_text())
            g = json.loads((d / "graph.json").read_text())
            out.append(
                RunSummary(
                    run_id=d.name,
                    query=m["query"],
                    created_at=m["created_at"],
                    papers=m["parsed"],
                    nodes=len(g["nodes"]),
                    edges=len(g["edges"]),
                )
            )
        return out


class JobStatus(BaseModel):
    run_id: str
    state: str = "queued"  # queued, running, done, error
    message: str = "Waiting to start"
    progress: float = 0.0
    error: str | None = None


class JobManager:
    """Runs pipelines in background threads. PubMed requests share one rate limiter."""

    def __init__(self, store: RunStore, workers: int = 2) -> None:
        self.store = store
        self._pool = ThreadPoolExecutor(max_workers=workers, thread_name_prefix="run")
        self._jobs: dict[str, JobStatus] = {}
        self._lock = threading.Lock()

    def submit(self, params: RunParams) -> JobStatus:
        run_id = new_run_id(params.query)
        with self._lock:
            # Two runs of the same query in the same second get distinct ids.
            base, n = run_id, 2
            while run_id in self._jobs or self.store.path(run_id).exists():
                run_id, n = f"{base}-{n}", n + 1
            status = self._jobs[run_id] = JobStatus(run_id=run_id)
        self._pool.submit(self._run, params, status)
        return status

    def status(self, run_id: str) -> JobStatus:
        with self._lock:
            if run_id in self._jobs:
                return self._jobs[run_id].model_copy()
        if (self.store.path(run_id) / "manifest.json").exists():
            return JobStatus(run_id=run_id, state="done", message="Done", progress=1.0)
        raise KeyError(run_id)

    def _run(self, params: RunParams, status: JobStatus) -> None:
        def report(message: str, progress: float) -> None:
            with self._lock:
                status.state, status.message, status.progress = "running", message, progress

        client = PubMedClient(cache_dir=self.store.cache_dir)
        try:
            report("Starting", 0.0)
            out = run_pipeline(params, status.run_id, client=client, progress=report)
            self.store.save(out)
            with self._lock:
                status.state, status.message, status.progress = "done", "Done", 1.0
        except Exception as exc:  # noqa: BLE001 - reported to the UI
            traceback.print_exc()
            with self._lock:
                status.state, status.error = "error", str(exc) or type(exc).__name__
                status.message = "Failed"
        finally:
            client.close()
