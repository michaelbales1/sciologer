"""The citation map: papers linked by how they cite.

A third view, alongside the entity and text maps. Citation links show intellectual
communities, where text shows topics (Xie & Waltman 2023). Data come from NIH iCite,
which covers PubMed and also provides the Relative Citation Ratio (RCR), a
field-normalized impact measure: 1.0 is the median for NIH-funded papers in the same
field and year.

Two papers in the result set are linked when
- one cites the other (direct citation), and/or
- they share references (bibliographic coupling), weighted by Salton's cosine:
  shared / sqrt(refs_a × refs_b), so long reference lists don't dominate.
Link weight = direct (1 if either cites the other) + coupling cosine. In a focused
topic nearly every pair of papers shares some reference, which makes the network almost
complete and its layout meaningless, so only each paper's strongest coupling links are
kept (plus every direct citation). Coupling works
for recent papers that haven't been cited yet; direct citation ties the set together
across time. This is close to how CWTS builds its publication-level classifications.

Clusters: Leiden on that network. Layout: two candidates, and the one whose map best
agrees with the clusters is kept (share of each paper's nearest map neighbors in its
own cluster; clusters come from the network, independently of either layout):
- ForceAtlas2 (LinLog), as in the entity map, which suits small, sparse networks;
- a spectral embedding of the network laid out with t-SNE, which suits large, dense
  ones where force layouts collapse into a ball.
On test sets each won on one kind of network (0.90 vs 0.50 agreement on a researcher's
82 linked papers; 0.40 vs 0.70 on 379 papers about one topic). Both scores and the
choice are recorded. Papers with no links to the rest of the set are listed, not drawn.
"""

from __future__ import annotations

import hashlib
import json
import math
import random
import threading
import time
from collections import defaultdict
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path

import httpx
import networkx as nx
import numpy as np
from pydantic import BaseModel, Field

from .cluster import leiden_communities
from .layout import LayoutParams, compute_layout
from .records import Paper
from .semantic import _ctfidf, paper_text, query_words

ICITE = "https://icite.od.nih.gov/api/pubs"
# PMIDs go in the URL; 200 keeps it near 2,000 characters (400 got 413 Too Large).
BATCH = 200
FIELDS = [
    "pmid",
    "year",
    "references",
    "cited_by",
    "citation_count",
    "relative_citation_ratio",
    "nih_percentile",
    "provisional",
    "is_research_article",
    "field_citation_rate",
]


class _Limiter:
    """Spaces iCite requests across all threads (no published limit, so be polite)."""

    def __init__(self, interval: float) -> None:
        self.interval = interval
        self._lock = threading.Lock()
        self._next = 0.0

    def wait(self, sleep: Callable[[float], None]) -> None:
        with self._lock:
            now = time.monotonic()
            start = max(now, self._next)
            self._next = start + self.interval
        if start > now:
            sleep(start - now)

    def penalize(self, seconds: float) -> None:
        with self._lock:
            self._next = max(self._next, time.monotonic() + seconds)


_LIMITER = _Limiter(0.5)


class ICiteError(RuntimeError):
    pass


class ICiteClient:
    def __init__(
        self,
        cache_dir: Path | None = None,
        transport: httpx.BaseTransport | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.cache_dir = cache_dir
        self._sleep = sleep
        self._http = httpx.Client(timeout=60, transport=transport)
        self.retries = 0
        self.from_cache = False
        self.retrieved_at: str | None = None

    def _get(self, pmids: list[str]) -> list[dict]:
        params = {"pmids": ",".join(pmids), "fl": ",".join(FIELDS)}
        for attempt in range(6):
            _LIMITER.wait(self._sleep)
            try:
                resp = self._http.get(ICITE, params=params)
            except httpx.TransportError:
                if attempt == 5:
                    raise
                self.retries += 1
                _LIMITER.penalize(min(30.0, 2**attempt) + random.uniform(0, 0.5))
                continue
            if resp.status_code == 429 or resp.status_code >= 500:
                self.retries += 1
                retry = resp.headers.get("Retry-After")
                delay = float(retry) if retry and retry.isdigit() else min(30.0, 2**attempt)
                _LIMITER.penalize(delay + random.uniform(0, 0.5))
                continue
            resp.raise_for_status()
            return resp.json().get("data", [])
        raise ICiteError("iCite kept refusing requests. Wait a minute and try again.")

    def fetch(self, pmids: list[str], use_cache: bool = True) -> dict[str, dict]:
        """iCite records keyed by PMID. PMIDs iCite doesn't know are simply absent."""
        pmids = sorted(set(pmids), key=int)
        cache = None
        if self.cache_dir is not None:
            digest = hashlib.sha256(",".join(pmids).encode()).hexdigest()[:16]
            cache = self.cache_dir / f"icite-{digest}.json"
            if use_cache and cache.exists():
                saved = json.loads(cache.read_text())
                self.from_cache = True
                self.retrieved_at = saved["retrieved_at"]
                return saved["records"]
        records: dict[str, dict] = {}
        for start in range(0, len(pmids), BATCH):
            for r in self._get(pmids[start : start + BATCH]):
                records[str(r["pmid"])] = r
        self.retrieved_at = datetime.now(UTC).isoformat()
        if cache is not None:
            cache.parent.mkdir(parents=True, exist_ok=True)
            cache.write_text(json.dumps({"retrieved_at": self.retrieved_at, "records": records}))
        return records

    def close(self) -> None:
        self._http.close()


class CitationParams(BaseModel):
    resolution: float = Field(1.0, ge=0.2, le=3.0)
    # Keep each paper's k strongest coupling links (direct citations are always kept).
    coupling_neighbors: int = Field(5, ge=1, le=50)
    min_cluster_size: int = 3
    seed: int = 42
    layout: LayoutParams = Field(default_factory=LayoutParams)


def _ids(value: object) -> set[str]:
    return {str(x) for x in value} if isinstance(value, list) else set()


def build_citation_map(
    papers: list[Paper],
    icite: dict[str, dict],
    p: CitationParams,
    query: str = "",
) -> tuple[dict, dict]:
    """Returns (citation_json, summary)."""
    in_set = {x.pmid for x in papers}
    found = [x for x in papers if x.pmid in icite]
    refs = {x.pmid: _ids(icite[x.pmid].get("references")) for x in found}
    summary: dict = {
        "papers": len(papers),
        "found_in_icite": len(found),
        "with_references": sum(1 for r in refs.values() if r),
        "with_rcr": sum(
            1 for x in found if icite[x.pmid].get("relative_citation_ratio") is not None
        ),
    }

    # Links: direct citations within the set, and shared references (cosine).
    direct: set[tuple[str, str]] = set()
    for a in refs:
        for b in refs[a] & in_set:
            if b != a and b in refs:
                direct.add(tuple(sorted((a, b))))
    citing: dict[str, set[str]] = defaultdict(set)  # reference -> papers in set citing it
    for a, rs in refs.items():
        for r in rs:
            citing[r].add(a)
    shared: dict[tuple[str, str], int] = defaultdict(int)
    for group in citing.values():
        if 1 < len(group) <= 200:  # a reference cited by most of the set says little
            g = sorted(group)
            for i, a in enumerate(g):
                for b in g[i + 1 :]:
                    shared[(a, b)] += 1

    cosine = {
        pair: n / math.sqrt(len(refs[pair[0]]) * len(refs[pair[1]])) for pair, n in shared.items()
    }
    # Backbone: a coupling link survives if it is among the k strongest for either paper.
    ranked: dict[str, list[tuple[float, tuple[str, str]]]] = defaultdict(list)
    for pair, c in cosine.items():
        ranked[pair[0]].append((c, pair))
        ranked[pair[1]].append((c, pair))
    kept_pairs: set[tuple[str, str]] = set()
    for links in ranked.values():
        links.sort(reverse=True)
        kept_pairs.update(pair for _, pair in links[: p.coupling_neighbors])

    g = nx.Graph()
    g.add_nodes_from(refs)
    for pair in kept_pairs | direct:
        a, b = pair
        is_direct = pair in direct
        g.add_edge(
            a,
            b,
            weight=(1.0 if is_direct else 0.0) + cosine.get(pair, 0.0),
            shared=shared.get(pair, 0),
            direct=is_direct,
        )
    unlinked = [n for n in g if g.degree(n) == 0]
    g.remove_nodes_from(unlinked)
    summary.update(
        {
            "direct_citations": len(direct),
            "coupled_pairs": len(shared),
            "coupling_links_kept": len(kept_pairs),
            "unlinked": len(unlinked),
            "mapped": g.number_of_nodes(),
        }
    )
    if g.number_of_nodes() < 5:
        summary["skipped"] = "Too few papers linked by citations for a citation map"
        return {"papers": [], "clusters": [], "edges": []}, summary

    # Clusters (Leiden), small groups left unclustered.
    comms = [
        c
        for c in leiden_communities(g, "weight", p.resolution, p.seed)
        if len(c) >= p.min_cluster_size
    ]
    label_of: dict[str, int] = {}
    for cid, members in enumerate(comms):
        for n in members:
            label_of[n] = cid
    summary["clusters"] = len(comms)
    summary["unclustered"] = g.number_of_nodes() - len(label_of)

    chosen = _best_layout(g, label_of, p)
    summary["layout"] = chosen["summary"]

    # Names: distinctive terms of the cluster's titles and abstracts.
    nodes = list(g.nodes)
    by_pmid = {x.pmid: x for x in papers}
    labels = np.array([label_of.get(n, -1) for n in nodes])
    terms = _ctfidf([paper_text(by_pmid[n]) for n in nodes], labels, exclude=query_words(query))

    out_papers = []
    for n in nodes:
        rec = icite[n]
        neighbors = sorted(g[n].items(), key=lambda kv: -kv[1]["weight"])
        out_papers.append(
            {
                "pmid": n,
                "x": chosen["x"][n],
                "y": chosen["y"][n],
                "stability": chosen["stability"][n],
                "cluster": label_of.get(n, -1),
                "rcr": rec.get("relative_citation_ratio"),
                "nih_percentile": rec.get("nih_percentile"),
                "provisional": bool(rec.get("provisional")),
                "citation_count": rec.get("citation_count") or 0,
                "is_research_article": rec.get("is_research_article"),
                "references_total": len(refs[n]),
                "cites": sorted(refs[n] & set(nodes)),
                "cited_by": sorted(_ids(rec.get("cited_by")) & set(nodes)),
                "coupled": [
                    {
                        "pmid": m,
                        "shared": e["shared"],
                        "cosine": round(e["weight"] - (1.0 if e["direct"] else 0.0), 3),
                    }
                    for m, e in neighbors
                    if e["shared"]
                ][:10],
            }
        )
    clusters = []
    for cid, members in enumerate(comms):
        # Most central: highest within-cluster link strength.
        strength = {n: sum(e["weight"] for m, e in g[n].items() if m in members) for n in members}
        top = sorted(members, key=lambda n: -strength[n])[:5]
        cl_terms = terms.get(cid, [])
        clusters.append(
            {
                "id": cid,
                "size": len(members),
                "label": " · ".join(t for t, _ in cl_terms[:3]) or f"Cluster {cid + 1}",
                "terms": cl_terms,
                "representative": top,
            }
        )
    edges = [
        {
            "source": a,
            "target": b,
            "weight": round(e["weight"], 4),
            "shared": e["shared"],
            "direct": e["direct"],
        }
        for a, b, e in g.edges(data=True)
    ]
    return {
        "papers": out_papers,
        "clusters": clusters,
        "edges": edges,
        "low_stability": chosen["low_stability"],
        "layout_method": chosen["method"],
    }, summary


def _agreement(xy: np.ndarray, labels: np.ndarray, k: int = 10) -> float:
    """Median share of each point's k nearest map neighbors in its own cluster."""
    d = np.linalg.norm(xy[:, None] - xy[None], axis=-1)
    np.fill_diagonal(d, np.inf)
    nb = np.argsort(d, axis=1)[:, : min(k, len(xy) - 1)]
    clustered = labels >= 0
    same = (labels[nb] == labels[:, None]).mean(axis=1)
    return round(float(np.median(same[clustered])) if clustered.any() else 0.0, 3)


def _best_layout(g: nx.Graph, label_of: dict[str, int], p: CitationParams) -> dict:
    from sklearn.manifold import TSNE, SpectralEmbedding

    from .layout import to_display
    from .semantic import _local_stability

    nodes = list(g.nodes)
    labels = np.array([label_of.get(n, -1) for n in nodes])

    # Candidate 1: ForceAtlas2 (writes x, y, stability onto a copy of the graph).
    h = g.copy()
    fa = compute_layout(h, p.layout)
    fa_xy = np.array([[h.nodes[n]["x"], h.nodes[n]["y"]] for n in nodes])
    fa_stab = {n: h.nodes[n]["stability"] for n in nodes}

    # Candidate 2: spectral embedding of the weighted network, then t-SNE.
    index = {n: i for i, n in enumerate(nodes)}
    w = np.zeros((len(nodes), len(nodes)))
    for a, b, e in g.edges(data=True):
        w[index[a], index[b]] = w[index[b], index[a]] = e["weight"]
    # Disconnected pieces are fine here (each lands in its own region); silence
    # scikit-learn's warning about them.
    import warnings

    with warnings.catch_warnings():
        warnings.filterwarnings("ignore", message="Graph is not fully connected")
        emb = SpectralEmbedding(
            n_components=min(30, len(nodes) - 2), affinity="precomputed", random_state=p.seed
        ).fit_transform(w)
    perplexity = float(min(30.0, (len(nodes) - 1) / 3))
    runs = []
    for i in range(3):
        y = TSNE(
            perplexity=perplexity,
            init="pca" if i == 0 else "random",
            random_state=p.seed + i,
        ).fit_transform(emb)
        y = y - y.mean(axis=0)
        runs.append(y / (np.sqrt((y**2).sum(axis=1).mean()) or 1))
    ts_xy = to_display(runs[0])
    ts_stab = _local_stability(runs, 10)

    scores = {
        "forceatlas2": _agreement(fa_xy, labels),
        "spectral_tsne": _agreement(ts_xy, labels),
    }
    use_fa = scores["forceatlas2"] >= scores["spectral_tsne"]
    if use_fa:
        xy, stab, low, method = fa_xy, [fa_stab[n] for n in nodes], 0.5, "forceatlas2"
        detail = "ForceAtlas2 (LinLog); stability = rank agreement of distances across 5 seeds"
    else:
        xy, stab, low, method = ts_xy, ts_stab, 0.3, "spectral_tsne"
        detail = (
            "spectral embedding + t-SNE; stability = share of map neighbors kept across "
            "3 starts; only local neighborhoods are meaningful"
        )
    stab = np.asarray(stab, dtype=float)
    return {
        "x": {n: round(float(xy[i, 0]), 5) for i, n in enumerate(nodes)},
        "y": {n: round(float(xy[i, 1]), 5) for i, n in enumerate(nodes)},
        "stability": {n: round(float(stab[i]), 3) for i, n in enumerate(nodes)},
        "low_stability": low,
        "method": method,
        "summary": {
            "method": method,
            "detail": detail,
            "cluster_agreement": scores,
            "median_stability": round(float(np.median(stab)), 3),
            "low_stability_nodes": int((stab < low).sum()),
            "forceatlas2_iterations": fa.get("runs") and p.layout.max_iter,
        },
    }
