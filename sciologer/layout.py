"""2D layout with a stability check.

ForceAtlas2 with LinLog attraction is the modern form of the LinLog energy model that
CCVisu used in 2008. A layout is one of many plausible pictures: it depends on the seed
and parameters (Atzberger et al. 2024). So we lay the graph out several times and, for
each node, compare the rank order of its distances to every other node between runs
(Spearman correlation). This ignores rotation and reflection, and swaps among nodes
that are interchangeable (entities that only ever appear on the same paper). Low scores
are drawn differently in the UI, so readers don't over-read where those nodes sit.
"""

from __future__ import annotations

import networkx as nx
import numpy as np
from pydantic import BaseModel

LOW_STABILITY = 0.5


class LayoutParams(BaseModel):
    max_iter: int = 300
    linlog: bool = True
    gravity: float = 1.0
    scaling_ratio: float = 2.0
    seed: int = 42
    stability_runs: int = 5


def _run(g: nx.Graph, p: LayoutParams, seed: int) -> dict[str, np.ndarray]:
    # networkx's LinLog divides by the zero self-distance before masking the diagonal.
    with np.errstate(divide="ignore", invalid="ignore"):
        return nx.forceatlas2_layout(
            g,
            max_iter=p.max_iter,
            linlog=p.linlog,
            gravity=p.gravity,
            scaling_ratio=p.scaling_ratio,
            weight="weight",
            seed=seed,
        )


def _normalize(pos: dict[str, np.ndarray], nodes: list[str]) -> np.ndarray:
    """Center and scale to unit RMS radius."""
    xy = np.nan_to_num(np.array([pos[n] for n in nodes], dtype=float))
    xy -= xy.mean(axis=0)
    rms = np.sqrt((xy**2).sum(axis=1).mean()) or 1.0
    return xy / rms


def _distance_ranks(xy: np.ndarray) -> np.ndarray:
    """Row i: ranks of node i's distances to all other nodes (self excluded)."""
    n = len(xy)
    d = np.linalg.norm(xy[:, None, :] - xy[None, :, :], axis=-1)
    off = ~np.eye(n, dtype=bool)
    d = d[off].reshape(n, n - 1)
    return np.argsort(np.argsort(d, axis=1), axis=1).astype(float)


def _rowwise_corr(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    a = a - a.mean(axis=1, keepdims=True)
    b = b - b.mean(axis=1, keepdims=True)
    denom = np.sqrt((a**2).sum(axis=1) * (b**2).sum(axis=1))
    return np.divide((a * b).sum(axis=1), denom, out=np.ones(len(a)), where=denom > 0)


def compute_layout(g: nx.Graph, p: LayoutParams) -> dict:
    """Writes x, y and stability (0–1) onto each node. Returns a summary."""
    nodes = list(g.nodes)
    n = len(nodes)
    if n == 0:
        return {"nodes": 0}
    if n == 1:
        g.nodes[nodes[0]].update(x=0.0, y=0.0, stability=1.0)
        return {"nodes": 1, "runs": 1, "median_stability": 1.0}

    seeds = [p.seed + i for i in range(max(1, p.stability_runs))]
    runs = [_normalize(_run(g, p, s), nodes) for s in seeds]
    ref = runs[0]

    stability = np.ones(n)
    if len(runs) > 1 and n > 2:
        ref_ranks = _distance_ranks(ref)
        corr = np.array([_rowwise_corr(ref_ranks, _distance_ranks(r)) for r in runs[1:]])
        stability = np.clip(np.median(corr, axis=0), 0, 1)

    # Scale to [-1, 1] for display.
    display = ref / (np.abs(ref).max() or 1.0)
    for i, node in enumerate(nodes):
        g.nodes[node].update(
            x=round(float(display[i, 0]), 5),
            y=round(float(display[i, 1]), 5),
            stability=round(float(stability[i]), 3),
        )
    return {
        "nodes": n,
        "runs": len(runs),
        "seeds": seeds,
        "median_stability": round(float(np.median(stability)), 3),
        "low_stability_nodes": int((stability < LOW_STABILITY).sum()),
    }
