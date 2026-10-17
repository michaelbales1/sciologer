"""Community detection, shared by the entity map and the text map.

Leiden (Traag, Waltman & van Eck 2019) rather than Louvain: Louvain can return
communities that are poorly connected or even disconnected inside, while Leiden
guarantees every community is connected and keeps refining until no node can move
to improve the partition. The objective is modularity with a resolution parameter
(RBConfiguration), the same one Louvain optimized, so the resolution setting keeps
its meaning: higher gives more, smaller clusters.
"""

from __future__ import annotations

import igraph as ig
import leidenalg
import networkx as nx
from pydantic import BaseModel, Field

from .records import EntityType


class ClusterParams(BaseModel):
    resolution: float = Field(1.0, ge=0.2, le=3.0)
    seed: int = 42


class Cluster(BaseModel):
    id: int
    size: int
    label: str
    # The entities the label is built from, so the label can be checked against them.
    top_members: list[dict]


# Preferred types for naming a cluster: subject terms describe a topic better than names.
_LABEL_TYPES = [EntityType.MESH, EntityType.KEYWORD, EntityType.SUBSTANCE]


def leiden_communities(
    g: nx.Graph, weight: str = "weight", resolution: float = 1.0, seed: int = 42
) -> list[set]:
    """Leiden communities of a networkx graph, largest first (ties by smallest node)."""
    if g.number_of_nodes() == 0:
        return []
    nodes = list(g.nodes)
    index = {n: i for i, n in enumerate(nodes)}
    edges = [(index[a], index[b]) for a, b in g.edges()]
    h = ig.Graph(n=len(nodes), edges=edges)
    weights = [float(d.get(weight, 1.0)) for _, _, d in g.edges(data=True)]
    partition = leidenalg.find_partition(
        h,
        leidenalg.RBConfigurationVertexPartition,
        weights=weights if edges else None,
        resolution_parameter=resolution,
        n_iterations=-1,  # iterate until the partition stops improving
        seed=seed,
    )
    communities = [{nodes[i] for i in members} for members in partition]
    return sorted(communities, key=lambda c: (-len(c), min(map(str, c))))


def detect_clusters(g: nx.Graph, params: ClusterParams) -> list[Cluster]:
    """Leiden communities, largest first. Writes `cluster` onto each node.

    The label is the cluster's most distinctive subject terms (descriptive, not
    generated); the most frequent members are kept alongside as evidence.
    """
    if g.number_of_nodes() == 0:
        return []
    communities = leiden_communities(g, "weight", params.resolution, params.seed)
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
