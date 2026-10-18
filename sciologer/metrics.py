"""Collaboration measures for comparing paper sets (journals, departments, fields).

Computed on the complete co-authorship network of a set: every author on every paper,
after the same name matching the maps use. The map shows only the top authors per
type, and measures computed on that subset would be biased, so the two are separate.

Papers with very long bylines (default: more than 100 authors) are left out of the
network measures, as is common in co-authorship studies: one 800-author consortium
paper adds 319,600 links and would swamp everything else. They still count in the
paper-level measures, and how many were left out is reported.

Every measure is defined in METRIC_INFO, which the batch report and the app show
alongside the numbers.
"""

from __future__ import annotations

import random
import statistics
from collections import Counter, defaultdict
from itertools import combinations

import networkx as nx
from pydantic import BaseModel, Field

from .cluster import leiden_communities
from .normalize import build_entities
from .records import EntityType, Paper


class MetricParams(BaseModel):
    max_authors_per_paper: int = Field(100, ge=2)
    # Shortest paths are averaged over BFS runs from this many sampled authors.
    path_samples: int = Field(300, ge=10)
    seed: int = 42


# name -> (label, description). Order is the column order of the comparison table.
METRIC_INFO: dict[str, tuple[str, str]] = {
    "papers": ("Papers", "Papers in the set (journal articles parsed from PubMed)."),
    "authors": ("Authors", "Distinct authors after name matching."),
    "authors_per_paper_mean": ("Authors per paper (mean)", "Mean byline length."),
    "authors_per_paper_median": ("Authors per paper (median)", "Median byline length."),
    "single_author_share": ("Single-author papers", "Share of papers with one author."),
    "large_team_share": ("Papers with 10+ authors", "Share of papers with ten or more authors."),
    "papers_per_author_mean": (
        "Papers per author (mean)",
        "Mean number of papers per author in the set.",
    ),
    "one_paper_author_share": (
        "Authors with one paper",
        "Share of authors who appear on only one paper in the set (transient authors).",
    ),
    "multi_institution_share": (
        "Multi-institution papers",
        "Share of papers whose authors list two or more distinct institutions, "
        "among papers with any affiliation.",
    ),
    "affiliation_coverage": (
        "Papers with affiliations",
        "Share of papers with at least one author affiliation in PubMed.",
    ),
    "network_papers_excluded": (
        "Papers left out of network",
        "Papers with more authors than the cap, excluded from network measures.",
    ),
    "network_edges": ("Co-author links", "Distinct pairs of authors who share at least one paper."),
    "density": (
        "Density",
        "Links present ÷ links possible. Small networks are denser, "
        "so compare sets of similar size.",
    ),
    "mean_degree": ("Co-authors per author (mean)", "Mean number of distinct co-authors."),
    "repeat_collaboration_share": (
        "Repeat collaborations",
        "Share of co-author pairs who share two or more papers.",
    ),
    "components": ("Components", "Separate groups with no co-authorship between them."),
    "largest_component_share": (
        "Largest component",
        "Share of authors in the biggest connected group.",
    ),
    "clustering": (
        "Clustering (average)",
        "Average local clustering: how often an author's co-authors "
        "also co-author with each other.",
    ),
    "transitivity": (
        "Transitivity",
        "Share of connected author triples that close into triangles.",
    ),
    "modularity": (
        "Modularity",
        "Strength of community structure (Leiden, fractional co-authorship weights).",
    ),
    "communities": ("Communities", "Communities found by Leiden in the co-authorship network."),
    "mean_path_length": (
        "Mean path length",
        "Average co-authorship distance between authors in the largest component (sampled).",
    ),
    "degree_assortativity": (
        "Degree assortativity",
        "Whether well-connected authors tend to co-author with each other (+) "
        "or with less-connected authors (−).",
    ),
}


def collaboration_metrics(papers: list[Paper], p: MetricParams | None = None) -> dict:
    p = p or MetricParams()
    out: dict[str, float | int | None] = {}
    table = build_entities(papers, [EntityType.AUTHOR, EntityType.INSTITUTION])
    by_paper = table.by_paper()
    author_prefix, inst_prefix = "author:", "institution:"
    authors_of = {
        x.pmid: sorted(k for k in by_paper.get(x.pmid, ()) if k.startswith(author_prefix))
        for x in papers
    }
    bylines = [len(x.authors) for x in papers if x.authors]

    out["papers"] = len(papers)
    authors = {a for keys in authors_of.values() for a in keys}
    out["authors"] = len(authors)
    out["authors_per_paper_mean"] = _r(statistics.fmean(bylines)) if bylines else None
    out["authors_per_paper_median"] = statistics.median(bylines) if bylines else None
    out["single_author_share"] = _share(sum(1 for b in bylines if b == 1), len(bylines))
    out["large_team_share"] = _share(sum(1 for b in bylines if b >= 10), len(bylines))
    per_author = Counter(a for keys in authors_of.values() for a in keys)
    out["papers_per_author_mean"] = (
        _r(statistics.fmean(per_author.values())) if per_author else None
    )
    out["one_paper_author_share"] = _share(
        sum(1 for c in per_author.values() if c == 1), len(per_author)
    )

    insts = {
        pmid: {k for k in keys if k.startswith(inst_prefix)} for pmid, keys in by_paper.items()
    }
    with_aff = [pm for pm, s in insts.items() if s]
    out["affiliation_coverage"] = _share(len(with_aff), len(papers))
    out["multi_institution_share"] = _share(
        sum(1 for pm in with_aff if len(insts[pm]) >= 2), len(with_aff)
    )

    # Co-authorship network, without hyper-authored papers.
    g = nx.Graph()
    g.add_nodes_from(authors)
    shared: Counter[tuple[str, str]] = Counter()
    fractional: defaultdict[tuple[str, str], float] = defaultdict(float)
    excluded = 0
    for x in papers:
        keys = authors_of[x.pmid]
        if len(x.authors) > p.max_authors_per_paper:
            excluded += 1
            continue
        if len(keys) < 2:
            continue
        share = 1 / (len(x.authors) - 1) if len(x.authors) > 1 else 1.0
        for a, b in combinations(keys, 2):
            shared[(a, b)] += 1
            fractional[(a, b)] += share
    for (a, b), n in shared.items():
        g.add_edge(a, b, papers=n, weight=fractional[(a, b)])
    out["network_papers_excluded"] = excluded
    out["network_edges"] = g.number_of_edges()
    n = g.number_of_nodes()
    out["density"] = _r(nx.density(g), 6) if n > 1 else None
    out["mean_degree"] = _r(2 * g.number_of_edges() / n) if n else None
    out["repeat_collaboration_share"] = _share(
        sum(1 for c in shared.values() if c >= 2), len(shared)
    )

    comps = list(nx.connected_components(g)) if n else []
    out["components"] = len(comps)
    largest = max(comps, key=len) if comps else set()
    out["largest_component_share"] = _share(len(largest), n)
    out["clustering"] = _r(nx.average_clustering(g)) if n else None
    out["transitivity"] = _r(nx.transitivity(g)) if n else None

    if g.number_of_edges():
        comms = leiden_communities(g, "weight", 1.0, p.seed)
        out["modularity"] = _r(nx.community.modularity(g, comms, weight="weight"))
        out["communities"] = sum(1 for c in comms if len(c) > 1)
        try:
            out["degree_assortativity"] = _r(nx.degree_assortativity_coefficient(g))
        except (ValueError, ZeroDivisionError):
            out["degree_assortativity"] = None
    else:
        out["modularity"] = out["communities"] = out["degree_assortativity"] = None

    out["mean_path_length"] = (
        _sampled_path_length(g.subgraph(largest), p) if len(largest) > 1 else None
    )
    return out


def _sampled_path_length(g: nx.Graph, p: MetricParams) -> float:
    """Mean shortest-path length, from BFS out of a random sample of sources."""
    nodes = sorted(g.nodes)
    rng = random.Random(p.seed)
    sources = nodes if len(nodes) <= p.path_samples else rng.sample(nodes, p.path_samples)
    total = count = 0
    for s in sources:
        for t, d in nx.single_source_shortest_path_length(g, s).items():
            if t != s:
                total += d
                count += 1
    return _r(total / count) if count else 0.0


def _share(part: int, whole: int) -> float | None:
    return _r(part / whole) if whole else None


def _r(x: float, digits: int = 4) -> float:
    return round(float(x), digits)
