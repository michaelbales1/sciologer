"""The full Sciologer pipeline: PubMed query → entities → network → clusters → layout."""

from __future__ import annotations

from collections.abc import Callable

import networkx as nx
from pydantic import BaseModel, Field

from .citations import CitationParams, ICiteClient, build_citation_map
from .cluster import ClusterParams, detect_clusters
from .geo import GeoParams, RORClient, build_geo_map
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
    # Fetch every matching record instead of the first max_records (used by batch
    # studies, where comparisons need complete sets, not PubMed's first N).
    retrieve_all: bool = False
    types: list[EntityType] = Field(default_factory=lambda: list(DEFAULT_TYPES))
    mesh_major_only: bool = False
    exclude_check_tags: bool = True
    network: NetworkParams = Field(default_factory=NetworkParams)
    cluster: ClusterParams = Field(default_factory=ClusterParams)
    layout: LayoutParams = Field(default_factory=LayoutParams)
    text_map: bool = True
    semantic: SemanticParams = Field(default_factory=SemanticParams)
    citation_map: bool = True
    citations: CitationParams = Field(default_factory=CitationParams)
    geo_map: bool = True
    geo: GeoParams = Field(default_factory=GeoParams)


class RunOutput(BaseModel):
    manifest: Manifest
    graph: dict
    papers: dict[str, dict]
    text: dict | None = None
    citations: dict | None = None
    geo: dict | None = None


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
    icite_client: ICiteClient | None = None,
    icite_records: dict[str, dict] | None = None,
    ror_client: RORClient | None = None,
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

        def fetched(done: int, total: int) -> None:
            report(f"Fetching records {done:,} of {total:,}", 0.05 + 0.45 * done / max(total, 1))

        try:
            if params.retrieve_all:
                search_result = client.search_all(params.query, progress=fetched)
            else:
                search_result = client.search(
                    params.query, max_records=params.max_records, progress=fetched
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
            )
            + (
                f"; all matching records, split into {len(search_result.parts)} publication-"
                "date ranges because PubMed returns at most 10,000 per search"
                if len(search_result.parts) > 1
                else "; all matching records"
                if params.retrieve_all
                else ""
            ),
            counts={
                "pubmed_matches": search_result.count,
                "fetched": manifest.fetched,
                "retries_after_rate_limit": search_result.retries,
                **({"date_ranges": search_result.parts} if len(search_result.parts) > 1 else {}),
            },
        )
    )
    if search_result.sampled_from:
        manifest.warnings.append(
            f"This map uses a random sample of {manifest.fetched:,} of the "
            f"{search_result.sampled_from:,} papers PubMed found."
        )
    elif search_result.count > manifest.fetched:
        if params.retrieve_all:
            truncated = [p["range"] for p in search_result.parts if p.get("truncated")]
            manifest.warnings.append(
                f"PubMed reported {search_result.count:,} matches but {manifest.fetched:,} "
                "were retrieved"
                + (
                    f"; these months had more than 10,000 records and were cut off: "
                    f"{', '.join(truncated)}."
                    if truncated
                    else " (PubMed's count and its date ranges can differ slightly)."
                )
            )
        else:
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
            detail=f"Leiden communities (modularity, resolution {params.cluster.resolution}, "
            f"seed {params.cluster.seed}); labels are the most distinctive subject terms "
            "(frequent in the cluster, mostly linked within it)",
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
                        f"Embedded title + abstract with {sp.model}; clustered with Leiden "
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

    # 8. Citation map (papers linked by shared references and direct citations)
    citations = None
    if params.citation_map:
        report("Fetching citation data from NIH iCite", 0.92)
        icite_meta: dict = {}
        if icite_records is None:
            own_icite = icite_client is None
            ic = icite_client or ICiteClient()
            try:
                icite_records = ic.fetch([x.pmid for x in papers])
                icite_meta = {
                    "from_cache": ic.from_cache,
                    "retrieved_at": ic.retrieved_at,
                    "retries_after_rate_limit": ic.retries,
                }
            except Exception as exc:  # noqa: BLE001 - the other views are still useful
                manifest.warnings.append(
                    f"Citation map skipped: couldn't get data from NIH iCite ({exc})."
                )
            finally:
                if own_icite:
                    ic.close()
        if icite_records is not None:
            report("Building citation map", 0.95)
            citations, cit_summary = build_citation_map(
                papers, icite_records, params.citations, query=params.query
            )
            if "skipped" in cit_summary:
                manifest.warnings.append(f"Citation map skipped: {cit_summary['skipped'].lower()}.")
                citations = None
            else:
                cp = params.citations
                manifest.steps.append(
                    Step(
                        name="Citation map",
                        detail=(
                            "Citation data from NIH iCite (references, citing papers, Relative "
                            "Citation Ratio). Papers in the set are linked if one cites the "
                            "other (weight 1) and/or they share references (Salton's cosine), "
                            f"keeping each paper's {cp.coupling_neighbors} strongest coupling "
                            f"links. Clusters: Leiden (resolution {cp.resolution}); groups "
                            f"under {cp.min_cluster_size} papers left unclustered. Layout: "
                            + cit_summary["layout"]["detail"]
                            + ", chosen over the alternative because its map agrees better "
                            "with the clusters (see cluster_agreement)."
                        ),
                        counts={**icite_meta, **cit_summary},
                    )
                )

    # 9. Geography (institutions from affiliations, placed with the ROR registry)
    geo = None
    if params.geo_map:
        report("Locating institutions with ROR", 0.96)
        own_ror = ror_client is None
        rc = ror_client or RORClient()
        try:
            geo, geo_summary = build_geo_map(
                papers, params.geo, rc, progress=lambda msg: report(msg, 0.97)
            )
        except Exception as exc:  # noqa: BLE001 - the other views are still useful
            manifest.warnings.append(f"Geographic view skipped: couldn't reach ROR ({exc}).")
            geo_summary = None
        finally:
            if own_ror:
                rc.close()
        if geo_summary and "skipped" in geo_summary:
            manifest.warnings.append(f"Geographic view skipped: {geo_summary['skipped'].lower()}.")
            geo = None
        elif geo_summary:
            gp = params.geo
            manifest.steps.append(
                Step(
                    name="Geography",
                    detail=(
                        "Each distinct affiliation (without emails or URLs) was matched with "
                        "the Research Organization Registry's affiliation service (ROR, "
                        "ror.org); only matches ROR marks as chosen (confident) are placed, "
                        f"at the city ROR records. Most frequent affiliations first, up to "
                        f"{gp.max_lookups:,}. Institutions are linked when they share a paper; "
                        f"the {gp.max_institutions} with the most papers are drawn."
                    ),
                    counts=geo_summary,
                )
            )
            if geo_summary["not_looked_up"]:
                manifest.warnings.append(
                    f"Geographic view: {geo_summary['not_looked_up']:,} rarer affiliations "
                    f"weren't looked up (limit {gp.max_lookups:,} per map)."
                )

    report("Done", 1.0)
    return RunOutput(
        manifest=manifest,
        graph=_graph_json(g, clusters, run_id, params.query),
        papers=_papers_json(papers),
        text=text,
        citations=citations,
        geo=geo,
    )
