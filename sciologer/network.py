"""Build a weighted co-occurrence network from entities.

Two entities are linked when they appear on the same paper. Edge weight is either the
raw number of shared papers, or log(1 + association strength); association strength
(VOSviewer's default normalization) divides by the expected co-occurrence so frequent
entities don't dominate.
"""

from __future__ import annotations

import math
from collections import Counter, defaultdict
from itertools import combinations
from typing import Literal

import networkx as nx
from pydantic import BaseModel

from .normalize import EntityTable
from .records import EntityType

Weighting = Literal["count", "association"]


class NetworkParams(BaseModel):
    min_occurrences: int = 2
    max_per_type: int = 60
    min_edge_weight: int = 1
    weighting: Weighting = "association"


def select_entities(table: EntityTable, params: NetworkParams) -> tuple[list[str], dict]:
    """Keep the most frequent entities of each type. Returns kept keys and a summary."""
    by_type: dict[EntityType, list] = defaultdict(list)
    for ent in table.entities.values():
        by_type[ent.type].append(ent)
    kept: list[str] = []
    summary = {}
    for t, ents in by_type.items():
        eligible = [e for e in ents if e.count >= params.min_occurrences]
        eligible.sort(key=lambda e: (-e.count, e.label.lower()))
        chosen = eligible[: params.max_per_type]
        kept.extend(e.key for e in chosen)
        summary[t.value] = {
            "found": len(ents),
            "met_min_occurrences": len(eligible),
            "shown": len(chosen),
        }
    return kept, summary


def build_graph(table: EntityTable, params: NetworkParams) -> tuple[nx.Graph, dict]:
    kept, summary = select_entities(table, params)
    keep = set(kept)
    pair_counts: Counter[tuple[str, str]] = Counter()
    for keys in table.by_paper().values():
        present = sorted(keys & keep)
        for a, b in combinations(present, 2):
            pair_counts[(a, b)] += 1

    g = nx.Graph()
    for key in kept:
        ent = table.entities[key]
        g.add_node(
            key,
            type=ent.type.value,
            label=ent.label,
            count=ent.count,
            pmids=sorted(ent.pmids),
            variants=sorted(ent.variants - {ent.label}),
            **ent.attrs,
        )

    total = sum(pair_counts.values()) or 1
    occurrences: Counter[str] = Counter()
    for (a, b), c in pair_counts.items():
        occurrences[a] += c
        occurrences[b] += c
    dropped_edges = 0
    for (a, b), c in pair_counts.items():
        if c < params.min_edge_weight:
            dropped_edges += 1
            continue
        attrs: dict[str, float] = {"count": c}
        if params.weighting == "association":
            expected = occurrences[a] * occurrences[b] / (2 * total)
            strength = c / expected if expected else 0.0
            # Raw association strength is heavy-tailed: two rare entities that share one
            # paper can score thousands of times the median and dominate clustering and
            # layout. log(1 + strength) keeps the ordering but tames the tail; on test
            # corpora it made clusters and layout agree (positive silhouette) where raw
            # weights did not.
            weight = math.log1p(strength)
            attrs["association"] = round(strength, 4)
        else:
            weight = float(c)
        g.add_edge(a, b, weight=weight, **attrs)

    summary["edges"] = {
        "pairs_found": len(pair_counts),
        "below_min_weight": dropped_edges,
        "shown": g.number_of_edges(),
    }
    # An entity with no links to anything else shown says nothing about relationships,
    # and a force layout would fling it to the edge. Leave it off the map, but list it.
    isolated = sorted((n for n in g if g.degree(n) == 0), key=lambda n: -g.nodes[n]["count"])
    summary["unlinked"] = [
        {"label": g.nodes[n]["label"], "type": g.nodes[n]["type"], "count": g.nodes[n]["count"]}
        for n in isolated
    ]
    g.remove_nodes_from(isolated)
    return g, summary
