"""Command line: `sciologer search` and `sciologer serve`."""

from __future__ import annotations

import threading
import webbrowser
from typing import Annotated

import typer

from .citations import ICiteClient
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
    try:
        out = run_pipeline(
            params,
            new_run_id(query),
            client=client,
            icite_client=icite,
            progress=lambda msg, frac: typer.echo(f"[{frac:4.0%}] {msg}", err=True),
        )
    finally:
        client.close()
        icite.close()
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
