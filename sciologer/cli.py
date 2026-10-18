"""Command line: `sciologer search` and `sciologer serve`."""

from __future__ import annotations

import threading
import webbrowser
from pathlib import Path
from typing import Annotated

import typer

from .citations import ICiteClient
from .geo import RORClient
from .pipeline import DEFAULT_TYPES, RunParams, run_pipeline
from .pubmed import PubMedClient
from .records import EntityType
from .runs import RunStore, new_run_id

app = typer.Typer(help="Explore relationships in PubMed search results.", no_args_is_help=True)


@app.command()
def search(
    query: Annotated[str, typer.Argument(help="PubMed query, e.g. 'neuroligins'")],
    types: Annotated[
        list[EntityType] | None,
        typer.Option("--type", "-t", help="Entity type to include (repeatable)"),
    ] = None,
    max_records: Annotated[int, typer.Option(help="Most records to fetch")] = 500,
    min_occurrences: Annotated[int, typer.Option(help="Hide entities on fewer papers")] = 2,
    no_cache: Annotated[bool, typer.Option(help="Ignore cached PubMed responses")] = False,
) -> None:
    """Run a query and save the map as a new run."""
    store = RunStore()
    params = RunParams(
        query=query,
        types=types or list(DEFAULT_TYPES),
        max_records=max_records,
        network={"min_occurrences": min_occurrences},
    )
    client = PubMedClient(cache_dir=None if no_cache else store.cache_dir)
    icite = ICiteClient(cache_dir=None if no_cache else store.cache_dir)
    ror = RORClient(cache_dir=None if no_cache else store.cache_dir)
    try:
        out = run_pipeline(
            params,
            new_run_id(query),
            client=client,
            icite_client=icite,
            ror_client=ror,
            progress=lambda msg, frac: typer.echo(f"[{frac:4.0%}] {msg}", err=True),
        )
    finally:
        client.close()
        icite.close()
        ror.close()
    path = store.save(out)
    m = out.manifest
    typer.echo(
        f"\nPubMed matches: {m.pubmed_count:,}   fetched: {m.fetched:,}   parsed: {m.parsed:,}"
    )
    typer.echo(
        f"Map: {len(out.graph['nodes'])} nodes, {len(out.graph['edges'])} links, "
        f"{len(out.graph['clusters'])} clusters"
    )
    for w in m.warnings:
        typer.secho(f"Note: {w}", fg="yellow")
    typer.echo(f"Saved to {path}")


@app.command()
def serve(
    port: Annotated[int, typer.Option(help="Port to listen on")] = 8765,
    open_browser: Annotated[bool, typer.Option("--open/--no-open")] = True,
) -> None:
    """Start the local web app (only reachable from this computer)."""
    import uvicorn

    from .api import create_app

    url = f"http://127.0.0.1:{port}/"
    if open_browser:
        threading.Timer(1.0, webbrowser.open, args=[url]).start()
    typer.echo(f"Sciologer is running at {url}  (Ctrl-C to stop)")
    uvicorn.run(create_app(), host="127.0.0.1", port=port, log_level="warning")


@app.command("runs")
def list_runs() -> None:
    """List saved runs."""
    for r in RunStore().list():
        typer.echo(f"{r.run_id}  {r.papers:>5} papers  {r.nodes:>4} nodes  {r.query}")


@app.command()
def batch(
    study_file: Annotated[Path, typer.Argument(help="Study file (YAML) listing the queries")],
    refresh: Annotated[
        bool, typer.Option(help="Search PubMed again instead of reusing frozen PMID lists")
    ] = False,
    only: Annotated[
        list[str] | None, typer.Option("--only", help="Run just this query label (repeatable)")
    ] = None,
) -> None:
    """Run every query in a study file, then write a comparison table.

    Progress is saved after each query: run the same command again to resume.
    On a Mac, prefix it with `caffeinate -i` to keep the computer awake.
    """
    from .study import StudyStore, load_study, run_study

    try:
        study = load_study(study_file)
    except Exception as exc:  # noqa: BLE001 - show the file problem plainly
        typer.secho(f"Can't read {study_file}: {exc}", fg="red", err=True)
        raise typer.Exit(1) from None
    store = StudyStore(RunStore())
    status = run_study(
        study,
        store,
        source_text=study_file.read_text(),
        refresh=refresh,
        only=only,
        log=lambda msg: typer.echo(msg, err=True),
    )
    qs = status["queries"]
    done = sum(1 for q in qs if q["state"] == "done")
    failed = [q["label"] for q in qs if q["state"] == "error"]
    folder = store.path(study.id)
    typer.echo(f"\n{done} of {len(qs)} queries done. Comparison table: {folder / 'metrics.csv'}")
    if failed:
        typer.secho(
            f"Failed: {', '.join(failed)}. Run the command again to retry them.", fg="yellow"
        )
    typer.echo("Open the app (sciologer serve) and choose Studies to compare them.")


@app.command()
def studies() -> None:
    """List batch studies."""
    from .study import StudyStore

    for st in StudyStore(RunStore()).list():
        failed = f", {st['failed']} failed" if st["failed"] else ""
        typer.echo(f"{st['id']}  {st['done']}/{st['queries']} done{failed}  {st['name']}")
