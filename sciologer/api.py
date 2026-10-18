"""Local web API and UI server."""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse, Response

from . import __version__
from .export import FORMATS, export
from .pipeline import DEFAULT_TYPES, RunParams
from .records import EntityType
from .runs import JobManager, JobStatus, RunStore, RunSummary
from .study import StudyStore

STATIC = Path(__file__).parent / "static"

_NOT_BUILT = """<!doctype html><meta charset="utf-8"><title>Sciologer</title>
<body style="font:16px system-ui;max-width:40rem;margin:4rem auto;padding:0 1rem">
<h1>Sciologer API is running</h1>
<p>The web interface hasn't been built yet. From the project folder run:</p>
<pre>cd web &amp;&amp; npm install &amp;&amp; npm run build</pre>
<p>Or, while developing, run <code>npm run dev</code> in <code>web/</code>.</p></body>"""


def create_app(store: RunStore | None = None) -> FastAPI:
    store = store or RunStore()
    jobs = JobManager(store)
    studies = StudyStore(store)
    app = FastAPI(title="Sciologer", version=__version__)
    app.state.store = store

    def missing(run_id: str) -> HTTPException:
        return HTTPException(404, f"No run named {run_id}")

    @app.get("/api/defaults")
    def defaults() -> dict:
        return {
            "params": RunParams(query="").model_dump(mode="json"),
            "types": [t.value for t in EntityType],
            "default_types": [t.value for t in DEFAULT_TYPES],
            "version": __version__,
        }

    @app.get("/api/runs")
    def list_runs() -> list[RunSummary]:
        return store.list()

    @app.post("/api/runs", status_code=202)
    def start_run(params: RunParams) -> JobStatus:
        if not params.query.strip():
            raise HTTPException(422, "Enter a PubMed query")
        if not params.types:
            raise HTTPException(422, "Choose at least one entity type")
        return jobs.submit(params)

    @app.get("/api/runs/{run_id}/status")
    def run_status(run_id: str) -> JobStatus:
        try:
            return jobs.status(run_id)
        except KeyError:
            raise missing(run_id) from None

    @app.get("/api/runs/{run_id}/geo/estimate")
    def geo_estimate(run_id: str) -> dict:
        """How many ROR lookups adding the geographic view needs, and roughly how long."""
        try:
            return jobs.geo_estimate(run_id)
        except KeyError:
            raise missing(run_id) from None

    @app.post("/api/runs/{run_id}/geo", status_code=202)
    def add_geo(run_id: str) -> JobStatus:
        """Add the geographic view to a saved map, in the background."""
        try:
            return jobs.submit_geo(run_id)
        except KeyError:
            raise missing(run_id) from None

    @app.get("/api/runs/{run_id}/geo/status")
    def geo_status(run_id: str) -> JobStatus:
        try:
            return jobs.geo_status(run_id)
        except KeyError:
            raise missing(run_id) from None

    @app.get("/api/runs/{run_id}")
    def get_run(run_id: str) -> dict:
        try:
            return {
                "graph": store.load(run_id, "graph"),
                "papers": store.load(run_id, "papers"),
                "manifest": store.load(run_id, "manifest"),
                "text": store.load_optional(run_id, "text"),
                "citations": store.load_optional(run_id, "citations"),
                "geo": store.load_optional(run_id, "geo"),
            }
        except KeyError:
            raise missing(run_id) from None

    @app.get("/api/runs/{run_id}/export/{fmt}")
    def export_run(run_id: str, fmt: str) -> Response:
        if fmt not in FORMATS:
            raise HTTPException(404, f"Unknown format {fmt}; choose from {', '.join(FORMATS)}")
        try:
            body = export(
                store.load(run_id, "graph"),
                store.load(run_id, "manifest"),
                fmt,
                text=store.load_optional(run_id, "text"),
                citations=store.load_optional(run_id, "citations"),
                geo=store.load_optional(run_id, "geo"),
                papers=store.load_optional(run_id, "papers"),
            )
        except KeyError:
            raise missing(run_id) from None
        media, ext = FORMATS[fmt]
        return Response(
            body,
            media_type=media,
            headers={"Content-Disposition": f'attachment; filename="sciologer-{run_id}.{ext}"'},
        )

    @app.get("/api/studies")
    def list_studies() -> list[dict]:
        return studies.list()

    @app.get("/api/studies/{study_id}")
    def get_study(study_id: str) -> dict:
        try:
            return studies.status(study_id)
        except KeyError:
            raise HTTPException(404, f"No study named {study_id}") from None

    @app.get("/api/studies/{study_id}/metrics.csv")
    def study_csv(study_id: str) -> Response:
        try:
            f = studies.path(study_id) / "metrics.csv"
        except KeyError:
            f = None
        if f is None or not f.exists():
            raise HTTPException(404, f"No study named {study_id}")
        return Response(
            f.read_text(),
            media_type="text/csv",
            headers={"Content-Disposition": f'attachment; filename="{study_id}-metrics.csv"'},
        )

    @app.get("/{path:path}", include_in_schema=False)
    def ui(path: str) -> Response:
        if path.startswith("api/"):
            raise HTTPException(404, "Not found")
        if not (STATIC / "index.html").exists():
            return HTMLResponse(_NOT_BUILT)
        f = (STATIC / path).resolve()
        if path and f.is_file() and f.is_relative_to(STATIC.resolve()) and f.name != "index.html":
            # Built assets have content hashes in their names, so they can be cached.
            return FileResponse(f, headers={"Cache-Control": "public, max-age=31536000, immutable"})
        return FileResponse(STATIC / "index.html", headers={"Cache-Control": "no-cache"})

    return app
