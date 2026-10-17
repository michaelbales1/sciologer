"""The full Sciologer pipeline: PubMed query → entities → network → clusters → layout."""

from __future__ import annotations

from collections.abc import Callable

import networkx as nx
from pydantic import BaseModel, Field

from .cluster import ClusterParams, detect_clusters
from .layout import LayoutParams, compute_layout
from .network import NetworkParams, build_graph
from .normalize import build_entities
from .parse import parse_batches
from .provenance import Manifest, Step
from .pubmed import PubMedClient, SearchResult
from .records import EntityType, Paper
from .semantic import Embedder, SemanticParams, build_text_map

# Text-map papers keeping fewer than this share of map neighbors across t-SNE starts
# are drawn as unstable.
TEXT_LOW_STABILITY = 0.3

DEFAULT_TYPES = [EntityType.MESH, EntityType.AUTHOR, EntityType.JOURNAL]

Progress = Callable[[str, float], None]


class RunParams(BaseModel):
    query: str
    max_records: int = Field(500, ge=1, le=10_000)
    types: list[EntityType] = Field(default_factory=lambda: list(DEFAULT_TYPES))
    mesh_major_only: bool = False
    exclude_check_tags: bool = True
    network: NetworkParams = Field(default_factory=NetworkParams)
    cluster: ClusterParams = Field(default_factory=ClusterParams)
    layout: LayoutParams = Field(default_factory=LayoutParams)
    text_map: bool = True
    semantic: SemanticParams = Field(default_factory=SemanticParams)


class RunOutput(BaseModel):
    manifest: Manifest
    graph: dict
    papers: dict[str, dict]
    text: dict | None = None


def _papers_json(papers: list[Paper]) -> dict[str, dict]:
    return {
        p.pmid: {
            "title": p.title,
            "year": p.year,
            "journal": p.journal_abbrev or p.journal,
            "doi": p.doi,
            "url": p.url,
            "authors": [a.display_name for a in p.authors[:6]]
            + (["et al."] if len(p.authors) > 6 else []),
        }
        for p in papers
    }


def _graph_json(g: nx.Graph, clusters: list, run_id: str, query: str) -> dict:
    nodes = []
    for key, d in g.nodes(data=True):
        nodes.append({"id": key, **d})
    edges = [
        {
            "source": a,
            "target": b,
            "weight": round(d["weight"], 4),
            "count": d["count"],
            **({"association": d["association"]} if "association" in d else {}),
        }
        for a, b, d in g.edges(data=True)
    ]
    return {
        "run_id": run_id,
        "query": query,
        "nodes": nodes,
        "edges": edges,
        "clusters": [c.model_dump() for c in clusters],
    }


def run_pipeline(
    params: RunParams,
    run_id: str,
    client: PubMedClient | None = None,
    search_result: SearchResult | None = None,
    progress: Progress | None = None,
    embed: Embedder | None = None,
) -> RunOutput:
    """Run every step. Pass `search_result` to skip the network fetch (tests, re-runs)."""
    report = progress or (lambda _msg, _frac: None)
    manifest = Manifest(
        run_id=run_id,
        query=params.query,
        parameters=params.model_dump(mode="json", exclude={"query"}),
    )

    # 1. Retrieve
    if search_result is None:
        own_client = client is None
        client = client or PubMedClient()
        report("Searching PubMed", 0.02)
        try:
            search_result = client.search(
                params.query,
                max_records=params.max_records,
                progress=lambda done, total: report(
                    f"Fetching records {done:,} of {total:,}", 0.05 + 0.45 * done / total
                ),
            )
        finally:
            if own_client:
                client.close()
    manifest.query_translation = search_result.query_translation
    manifest.pubmed_count = search_result.count
    manifest.fetched = len(search_result.pmids)
    manifest.from_cache = search_result.from_cache
    manifest.retrieved_at = search_result.retrieved_at
    manifest.pmids = search_result.pmids
    manifest.steps.append(
        Step(
            name="Retrieve",
            detail=(
                "Loaded from local cache of an earlier identical query"
                if search_result.from_cache
                else "Queried PubMed and fetched full records"
            ),
            counts={
                "pubmed_matches": search_result.count,
                "fetched": manifest.fetched,
                "retries_after_rate_limit": search_result.retries,
            },
        )
    )
    if search_result.count > manifest.fetched:
        manifest.warnings.append(
            f"PubMed found {search_result.count:,} records; this map uses the first "
            f"{manifest.fetched:,} in PubMed's default order. Narrow the query or raise "
            "the record limit to cover more."
        )

    # 2. Parse
    report("Parsing records", 0.55)
    papers = parse_batches(search_result.xml_batches)
    manifest.parsed = len(papers)
    manifest.steps.append(
        Step(
            name="Parse",
            detail="PubMed XML → papers with authors, affiliations, MeSH, substances, "
            "grants and keywords",
            counts={
                "papers": len(papers),
                "with_abstract": sum(1 for p in papers if p.abstract),
                "with_mesh": sum(1 for p in papers if p.mesh),
            },
        )
    )
    if len(papers) < manifest.fetched:
        manifest.warnings.append(
            f"{manifest.fetched - len(papers):,} fetched record(s) were not journal articles "
            "(for example, book chapters) and were left out."
        )

    # 3. Normalize
    report("Normalizing names", 0.6)
    table = build_entities(
        papers,
        params.types,
        mesh_major_only=params.mesh_major_only,
        exclude_check_tags=params.exclude_check_tags,
    )
    manifest.normalization = table.log
    manifest.steps.append(
        Step(
            name="Normalize",
            detail="Matched name variants; every merge is listed below, uncertain ones flagged",
            counts={
                "entities": len(table.entities),
                "merges": sum(1 for e in table.log if e.action == "merged"),
                "uncertain": sum(1 for e in table.log if e.uncertain),
            },
        )
    )

    # 4. Network
    report("Building network", 0.65)
    g, net_summary = build_graph(
        table, params.network, authors_per_paper={p.pmid: len(p.authors) for p in papers}
    )
    manifest.steps.append(
        Step(
            name="Network",
            detail=(
                "Linked entities that share a paper"
                + (
                    " (authors counted fractionally: on a paper with A authors, each earns "
                    "1/A credit and each author–author link counts 1/(A − 1); authors shown "
                    "are those with the most credit)"
                    if params.network.author_counting == "fractional"
                    else ""
                )
                + f"; kept up to {params.network.max_per_type} "
                f"per type seen on ≥{params.network.min_occurrences} papers; edge weight = "
                + (
                    "log(1 + association strength), where association strength = "
                    "shared papers ÷ expected shared papers"
                    if params.network.weighting == "association"
                    else "number of shared papers"
                )
            ),
            counts=net_summary,
        )
    )
    if net_summary["unlinked"]:
        n_unlinked = len(net_summary["unlinked"])
        manifest.warnings.append(
            f"{n_unlinked} entit{'y' if n_unlinked == 1 else 'ies'} had no links to anything "
            f"else shown and {'is' if n_unlinked == 1 else 'are'} listed instead of drawn."
        )
    if g.number_of_nodes() == 0:
        manifest.warnings.append(
            "No entity appears on enough papers to be shown. Lower the minimum "
            "occurrences or broaden the query."
        )

    # 5. Cluster
    report("Finding clusters", 0.7)
    clusters = detect_clusters(g, params.cluster)
    manifest.steps.append(
        Step(
            name="Cluster",
            detail=f"Louvain communities (resolution {params.cluster.resolution}, "
            f"seed {params.cluster.seed}); labels are the most frequent subject terms",
            counts={"clusters": len(clusters), "sizes": [c.size for c in clusters[:12]]},
        )
    )

    # 6. Layout
    report("Laying out the map", 0.75)
    layout_summary = compute_layout(g, params.layout)
    manifest.steps.append(
        Step(
            name="Layout",
            detail=(
                "ForceAtlas2 "
                + ("with LinLog attraction " if params.layout.linlog else "")
                + f"({params.layout.max_iter} iterations), repeated with "
                f"{layout_summary.get('runs', 0)} seeds to score how stable each node's "
                "neighborhood is. Positions are for navigation; distances are approximate."
            ),
            counts=layout_summary,
        )
    )

    # 7. Text map (a separate view: papers placed by what their abstracts say)
    text = None
    if params.text_map:
        sp = params.semantic
        text, text_summary = build_text_map(
            papers,
            sp,
            query=params.query,
            embed=embed,
            progress=lambda msg: report(msg, 0.85),
        )
        if "skipped" in text_summary:
            manifest.warnings.append(f"Text map skipped: {text_summary['skipped'].lower()}.")
            text = None
        else:
            text["low_stability"] = TEXT_LOW_STABILITY
            manifest.steps.append(
                Step(
                    name="Text map",
                    detail=(
                        f"Embedded title + abstract with {sp.model}; clustered with Louvain "
                        f"on a {sp.neighbors}-nearest-neighbor similarity graph in the full "
                        f"embedding space (groups under {sp.min_cluster_size} papers left "
                        "unclustered); labeled by class-based TF-IDF without the query's own "
                        f"words; projected with t-SNE (perplexity {text_summary['perplexity']}). "
                        "On the map, only local neighborhoods are meaningful."
                    ),
                    counts=text_summary,
                )
            )
            if text_summary.get("silhouette", 1) < 0.1:
                manifest.warnings.append(
                    f"Text clusters overlap heavily (silhouette {text_summary['silhouette']}); "
                    "read them as broad themes, not distinct groups."
                )

    report("Done", 1.0)
    return RunOutput(
        manifest=manifest,
        graph=_graph_json(g, clusters, run_id, params.query),
        papers=_papers_json(papers),
        text=text,
    )
