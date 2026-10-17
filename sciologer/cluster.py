"""Community detection on the co-occurrence network."""

from __future__ import annotations

import networkx as nx
from pydantic import BaseModel

from .records import EntityType


class ClusterParams(BaseModel):
    resolution: float = 1.0
    seed: int = 42


class Cluster(BaseModel):
    id: int
    size: int
    label: str
    # The entities the label is built from, so the label can be checked against them.
    top_members: list[dict]


# Preferred types for naming a cluster: subject terms describe a topic better than names.
_LABEL_TYPES = [EntityType.MESH, EntityType.KEYWORD, EntityType.SUBSTANCE]


def detect_clusters(g: nx.Graph, params: ClusterParams) -> list[Cluster]:
    """Louvain communities, largest first. Writes `cluster` onto each node.

    The label is the cluster's most distinctive subject terms (descriptive, not
    generated); the most frequent members are kept alongside as evidence.
    """
    if g.number_of_nodes() == 0:
        return []
    communities = nx.community.louvain_communities(
        g, weight="weight", resolution=params.resolution, seed=params.seed
    )
    communities = sorted(communities, key=lambda c: (-len(c), min(c)))
    clusters = []
    for cid, members in enumerate(communities):
        for n in members:
            g.nodes[n]["cluster"] = cid
        ranked = sorted(members, key=lambda n: (-g.nodes[n]["count"], g.nodes[n]["label"]))

        # Name the cluster by terms that are both frequent and mostly tied to this
        # cluster: count × share of the node's link weight that stays inside it.
        def distinctive(n: str, members: set = members) -> float:
            total = sum(d["weight"] for _, _, d in g.edges(n, data=True)) or 1.0
            inside = sum(d["weight"] for _, m, d in g.edges(n, data=True) if m in members)
            return g.nodes[n]["count"] * inside / total

        subject = sorted(
            (n for n in members if g.nodes[n]["type"] in _LABEL_TYPES),
            key=lambda n: (-distinctive(n), g.nodes[n]["label"]),
        )
        words: list[str] = []
        for n in subject or ranked:
            label = g.nodes[n]["label"]
            if label.casefold() not in {w.casefold() for w in words}:
                words.append(label)
            if len(words) == 3:
                break
        clusters.append(
            Cluster(
                id=cid,
                size=len(members),
                label=" · ".join(words),
                top_members=[
                    {
                        "key": n,
                        "label": g.nodes[n]["label"],
                        "type": g.nodes[n]["type"],
                        "count": g.nodes[n]["count"],
                    }
                    for n in ranked[:8]
                ],
            )
        )
    return clusters
