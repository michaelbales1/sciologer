"""Build a weighted co-occurrence network from entities.

Two entities are linked when they appear on the same paper. Edge weight is either the
raw number of shared papers, or log(1 + association strength); association strength
(VOSviewer's default normalization) divides by the expected co-occurrence so frequent
entities don't dominate.

Authors can be counted fractionally (Perianes-Rodriguez, Waltman & van Eck 2016), the
default. On a paper with A authors:
- each author earns 1/A credit for it, and the authors shown are those with the most
  credit (still requiring the minimum number of papers);
- each author–author link adds 1/(A − 1) instead of 1, so every author gets one unit of
  co-authorship per paper however long the byline is.
With full counting, a consortium paper with 80 authors counts as fully for each of them
as a two-author paper, and adds 3,160 full-strength links. A few such papers can fill
the author slots and bind their authors into one block that dominates the map. Both
parts matter: association strength largely cancels fractional links on their own.
"""

from __future__ import annotations

import math
from collections import Counter, defaultdict
from itertools import combinations
from typing import Literal

import networkx as nx
from pydantic import BaseModel, Field

from .normalize import EntityTable
from .records import EntityType

Weighting = Literal["count", "association"]
Counting = Literal["full", "fractional"]


class NetworkParams(BaseModel):
    min_occurrences: int = Field(2, ge=1, le=50)
    # Layout cost grows with the square of the node count; past ~200 per type maps
    # also get too crowded to read.
    max_per_type: int = Field(60, ge=5, le=200)
    min_edge_weight: int = Field(1, ge=1)
    weighting: Weighting = "association"
    # How authors are counted, for both ranking and author–author links (see above).
    author_counting: Counting = "fractional"


def author_credit(table: EntityTable, authors_per_paper: dict[str, int]) -> dict[str, float]:
    """Fractional credit per author entity: the sum of 1/A over their papers."""
    return {
        e.key: sum(1 / max(1, authors_per_paper.get(p, 1)) for p in e.pmids)
        for e in table.entities.values()
        if e.type == EntityType.AUTHOR
    }


def select_entities(
    table: EntityTable, params: NetworkParams, credit: dict[str, float] | None = None
) -> tuple[list[str], dict]:
    """Keep the top entities of each type: by fractional credit for authors when given,
    otherwise by number of papers. Returns kept keys and a summary."""
    by_type: dict[EntityType, list] = defaultdict(list)
    for ent in table.entities.values():
        by_type[ent.type].append(ent)
    kept: list[str] = []
    summary = {}
    for t, ents in by_type.items():
        eligible = [e for e in ents if e.count >= params.min_occurrences]
        if t == EntityType.AUTHOR and credit is not None:
            eligible.sort(key=lambda e: (-credit[e.key], -e.count, e.label.lower()))
        else:
            eligible.sort(key=lambda e: (-e.count, e.label.lower()))
        chosen = eligible[: params.max_per_type]
        kept.extend(e.key for e in chosen)
        summary[t.value] = {
            "found": len(ents),
            "met_min_occurrences": len(eligible),
            "shown": len(chosen),
        }
    return kept, summary


def build_graph(
    table: EntityTable,
    params: NetworkParams,
    authors_per_paper: dict[str, int] | None = None,
) -> tuple[nx.Graph, dict]:
    """`authors_per_paper` (pmid → length of the full byline) enables fractional
    counting of authors; without it, everything is counted in full."""
    fractional = params.author_counting == "fractional" and authors_per_paper is not None
    credit = author_credit(table, authors_per_paper) if fractional else None
    kept, summary = select_entities(table, params, credit)
    keep = set(kept)
    pair_counts: Counter[tuple[str, str]] = Counter()  # shared papers
    pair_weight: defaultdict[tuple[str, str], float] = defaultdict(float)  # counted strength
    author_prefix = EntityType.AUTHOR.value + ":"
    for pmid, keys in table.by_paper().items():
        present = sorted(keys & keep)
        byline = (authors_per_paper or {}).get(pmid, 0)
        co_author_share = 1 / (byline - 1) if fractional and byline > 1 else 1.0
        for a, b in combinations(present, 2):
            pair_counts[(a, b)] += 1
            both_authors = a.startswith(author_prefix) and b.startswith(author_prefix)
            pair_weight[(a, b)] += co_author_share if both_authors else 1.0

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
            **({"credit": round(credit[key], 3)} if credit and key in credit else {}),
        )

    total = sum(pair_weight.values()) or 1
    occurrences: defaultdict[str, float] = defaultdict(float)
    for (a, b), w in pair_weight.items():
        occurrences[a] += w
        occurrences[b] += w
    dropped_edges = 0
    for (a, b), c in pair_counts.items():
        if c < params.min_edge_weight:
            dropped_edges += 1
            continue
        w = pair_weight[(a, b)]
        attrs: dict[str, float] = {"count": c}
        if w != c:
            attrs["counted"] = round(w, 4)
        if params.weighting == "association":
            expected = occurrences[a] * occurrences[b] / (2 * total)
            strength = w / expected if expected else 0.0
            # Raw association strength is heavy-tailed: two rare entities that share one
            # paper can score thousands of times the median and dominate clustering and
            # layout. log(1 + strength) keeps the ordering but tames the tail; on test
            # corpora it made clusters and layout agree (positive silhouette) where raw
            # weights did not.
            weight = math.log1p(strength)
            attrs["association"] = round(strength, 4)
        else:
            weight = float(w)
        g.add_edge(a, b, weight=weight, **attrs)

    summary["edges"] = {
        "pairs_found": len(pair_counts),
        "below_min_weight": dropped_edges,
        "shown": g.number_of_edges(),
    }
    if fractional:
        bylines = [n for n in (authors_per_paper or {}).values() if n > 1]
        summary["author_counting"] = {
            "method": "fractional (ranking and author–author links)",
            "papers_with_10_plus_authors": sum(1 for n in bylines if n >= 10),
            "longest_byline": max(bylines, default=0),
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
