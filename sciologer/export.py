"""Export a run's network for other tools (Gephi, VOSviewer, Cytoscape, spreadsheets)."""

from __future__ import annotations

import csv
import io
import json
from collections.abc import Iterator

import networkx as nx

FORMATS = {
    "graphml": ("application/xml", "graphml"),
    "gexf": ("application/xml", "gexf"),
    "nodes.csv": ("text/csv", "nodes.csv"),
    "edges.csv": ("text/csv", "edges.csv"),
    "json": ("application/json", "json"),
    "text.csv": ("text/csv", "text-map.csv"),
}

_NODE_FIELDS = ["id", "label", "type", "count", "cluster", "x", "y", "stability"]


def _flat(value: object) -> object:
    if isinstance(value, list):
        return "; ".join(map(str, value))
    return value


def to_networkx(graph: dict) -> nx.Graph:
    g = nx.Graph(query=graph["query"], run_id=graph["run_id"])
    for n in graph["nodes"]:
        g.add_node(n["id"], **{k: _flat(v) for k, v in n.items() if k != "id"})
    for e in graph["edges"]:
        g.add_edge(e["source"], e["target"], weight=e["weight"], count=e["count"])
    return g


def _csv(rows: Iterator[list]) -> str:
    buf = io.StringIO()
    csv.writer(buf).writerows(rows)
    return buf.getvalue()


def export(
    graph: dict,
    manifest: dict,
    fmt: str,
    text: dict | None = None,
    papers: dict | None = None,
) -> str:
    if fmt == "graphml":
        return "\n".join(nx.generate_graphml(to_networkx(graph)))
    if fmt == "gexf":
        g = to_networkx(graph)
        # GEXF viewers read node position from viz attributes.
        for _, d in g.nodes(data=True):
            d["viz"] = {"position": {"x": d["x"] * 1000, "y": d["y"] * 1000, "z": 0.0}}
        return "\n".join(nx.generate_gexf(g))
    if fmt == "nodes.csv":
        return _csv(
            iter(
                [[*_NODE_FIELDS, "pmids"]]
                + [
                    [n.get(f, "") for f in _NODE_FIELDS] + [_flat(n["pmids"])]
                    for n in graph["nodes"]
                ]
            )
        )
    if fmt == "edges.csv":
        return _csv(
            iter(
                [["source", "target", "weight", "shared_papers"]]
                + [[e["source"], e["target"], e["weight"], e["count"]] for e in graph["edges"]]
            )
        )
    if fmt == "json":
        return json.dumps({"manifest": manifest, "graph": graph, "text_map": text}, indent=2)
    if fmt == "text.csv":
        if not text:
            raise KeyError(fmt)
        labels = {c["id"]: c["label"] for c in text["clusters"]}
        papers = papers or {}
        return _csv(
            iter(
                [
                    [
                        "pmid",
                        "title",
                        "year",
                        "journal",
                        "cluster",
                        "cluster_label",
                        "x",
                        "y",
                        "stability",
                        "text_neighbors_kept_on_map",
                        "nearest_by_text",
                    ]
                ]
                + [
                    [
                        p["pmid"],
                        papers.get(p["pmid"], {}).get("title", ""),
                        papers.get(p["pmid"], {}).get("year", ""),
                        papers.get(p["pmid"], {}).get("journal", ""),
                        p["cluster"],
                        labels.get(p["cluster"], "unclustered"),
                        p["x"],
                        p["y"],
                        p["stability"],
                        p["neighbors_kept"],
                        "; ".join(p["neighbors"]),
                    ]
                    for p in text["papers"]
                ]
            )
        )
    raise KeyError(fmt)
