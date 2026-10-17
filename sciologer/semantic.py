"""The text map: papers placed by what their titles and abstracts say.

A separate view from the entity co-occurrence map, not blended into it: text-based and
link-based maps answer different questions (Xie & Waltman 2023).

1. Embed title + abstract with a biomedical sentence-embedding model. The default is a
   Model2Vec distillation of PubMedBERT Embeddings: CPU only, ~31 MB, Apache-2.0.
2. Cluster in the full embedding space, not the 2D picture, so clusters don't inherit
   projection distortions: Leiden on a k-nearest-neighbor similarity graph. Groups
   smaller than a minimum size are left unclustered rather than shown as clusters.
3. Name each cluster by its most distinctive terms (class-based TF-IDF), keeping the
   term weights and the most central papers as evidence.
4. Project to 2D with t-SNE several times; score each paper's stability (do its map
   neighbors stay the same across starts?) and how many of its nearest papers by text
   stay near it on the map. A 2D map can't
   preserve all neighborhoods (González-Márquez et al. 2024), so both are reported.
"""

from __future__ import annotations

import re
from collections.abc import Callable

import networkx as nx
import numpy as np
from pydantic import BaseModel
from sklearn.decomposition import PCA
from sklearn.feature_extraction.text import ENGLISH_STOP_WORDS, CountVectorizer
from sklearn.manifold import TSNE
from sklearn.metrics import silhouette_score

from .cluster import leiden_communities
from .layout import to_display
from .records import Paper

DEFAULT_MODEL = "NeuML/pubmedbert-base-embeddings-8M"

# Words common to almost any abstract; they say nothing about a cluster's topic.
_ABSTRACT_WORDS = {
    "study",
    "studies",
    "results",
    "result",
    "using",
    "used",
    "use",
    "showed",
    "shown",
    "show",
    "based",
    "including",
    "included",
    "associated",
    "analysis",
    "found",
    "role",
    "data",
    "effect",
    "effects",
    "potential",
    "significant",
    "significantly",
    "however",
    "conclusion",
    "conclusions",
    "methods",
    "method",
    "background",
    "objective",
    "aim",
    "present",
    "new",
    "novel",
    "important",
    "suggest",
    "suggests",
    "observed",
    "related",
    "different",
    "high",
    "increased",
    "compared",
    "revealed",
    "demonstrated",
    "provide",
    "provides",
    "investigated",
    "identified",
    "involved",
    "known",
    "previously",
    "review",
}
STOP_WORDS = sorted(ENGLISH_STOP_WORDS | _ABSTRACT_WORDS)

Embedder = Callable[[list[str]], np.ndarray]


class SemanticParams(BaseModel):
    model: str = DEFAULT_MODEL
    neighbors: int = 10  # k for the similarity graph and the neighbor lists
    resolution: float = 1.0
    min_cluster_size: int = 5
    perplexity: float = 30.0
    seed: int = 42
    stability_runs: int = 3


def paper_text(p: Paper) -> str:
    return ". ".join(t for t in (p.title.strip().rstrip("."), p.abstract.strip()) if t)


def load_embedder(model: str) -> Embedder:
    from model2vec import StaticModel

    # force_download=False: use the local Hugging Face cache after the first run.
    m = StaticModel.from_pretrained(model, force_download=False)
    return lambda texts: np.asarray(m.encode(texts), dtype=np.float32)


def _normalize(e: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(e, axis=1, keepdims=True)
    return e / np.where(norms == 0, 1, norms)


def _knn(e: np.ndarray, k: int) -> tuple[np.ndarray, np.ndarray]:
    """Indices and cosine similarities of each row's k nearest other rows."""
    sim = e @ e.T
    np.fill_diagonal(sim, -np.inf)
    k = min(k, len(e) - 1)
    idx = np.argsort(-sim, axis=1)[:, :k]
    return idx, np.take_along_axis(sim, idx, axis=1)


def _map_neighbors(y: np.ndarray, k: int) -> np.ndarray:
    d = np.linalg.norm(y[:, None] - y[None], axis=-1)
    np.fill_diagonal(d, np.inf)
    return np.argsort(d, axis=1)[:, :k]


def _local_stability(runs: list[np.ndarray], k: int) -> np.ndarray:
    """Per paper, the mean share of its k nearest map neighbors in the reference run
    that are also among its k nearest in each other run. t-SNE keeps local
    neighborhoods but places whole clusters arbitrarily, so local overlap is the
    meaningful stability here."""
    n = len(runs[0])
    if len(runs) < 2 or n < 3:
        return np.ones(n)
    k = min(k, n - 1)
    ref = _map_neighbors(runs[0], k)
    scores = np.zeros(n)
    for other in runs[1:]:
        nb = _map_neighbors(other, k)
        scores += [len(set(a) & set(b)) / k for a, b in zip(ref, nb, strict=True)]
    return scores / (len(runs) - 1)


def _clusters(idx: np.ndarray, sims: np.ndarray, p: SemanticParams) -> np.ndarray:
    """Leiden on the kNN similarity graph; groups under min_cluster_size get -1."""
    g = nx.Graph()
    g.add_nodes_from(range(len(idx)))
    for i, (row, srow) in enumerate(zip(idx, sims, strict=True)):
        for j, s in zip(row, srow, strict=True):
            w = max(float(s), 1e-6)
            if g.has_edge(i, int(j)):
                g[i][int(j)]["weight"] = max(g[i][int(j)]["weight"], w)
            else:
                g.add_edge(i, int(j), weight=w)
    comms = [
        c
        for c in leiden_communities(g, "weight", p.resolution, p.seed)
        if len(c) >= p.min_cluster_size
    ]
    labels = np.full(len(idx), -1)
    for cid, members in enumerate(comms):
        labels[list(members)] = cid
    return labels


def query_words(query: str) -> set[str]:
    """Words of the PubMed query (minus field tags and operators). Every paper matches
    them, so they can't distinguish one cluster from another."""
    query = re.sub(r"\[[^\]]*\]", " ", query)
    words = set(re.findall(r"[a-zA-Z][a-zA-Z0-9-]+", query.lower())) - {"and", "or", "not"}
    # Include simple plural/singular variants ("neuroligins" ~ "neuroligin").
    return words | {w.rstrip("s") for w in words} | {w + "s" for w in words}


def _ctfidf(
    texts: list[str], labels: np.ndarray, exclude: set[str] = frozenset(), top: int = 8
) -> dict[int, list[tuple[str, float]]]:
    """Class-based TF-IDF: terms frequent in one cluster and rare in the others.

    BERTopic's weighting (tf × log(1 + avg words per cluster / term frequency)), times
    a cluster-spread factor log(1 + n_clusters / clusters containing the term), so a
    word that is common in every cluster can't name any of them.
    """
    ids = sorted(int(c) for c in set(labels) if c >= 0)
    if not ids:
        return {}
    vec = CountVectorizer(
        stop_words=sorted(set(STOP_WORDS) | set(exclude)),
        ngram_range=(1, 2),
        token_pattern=r"(?u)\b[a-zA-Z][a-zA-Z0-9-]{2,}\b",
        lowercase=True,
    )
    x = vec.fit_transform(texts)  # papers × terms
    terms = vec.get_feature_names_out()
    counts = np.vstack([np.asarray(x[labels == c].sum(axis=0)).ravel() for c in ids]).astype(float)
    papers_with = np.vstack([np.asarray((x[labels == c] > 0).sum(axis=0)).ravel() for c in ids])
    sizes = np.array([(labels == c).sum() for c in ids])
    tf = counts / np.maximum(counts.sum(axis=1, keepdims=True), 1)
    avg_words = counts.sum() / len(ids)
    idf = np.log(1 + avg_words / np.maximum(counts.sum(axis=0), 1))
    spread = np.log(1 + len(ids) / np.maximum((counts > 0).sum(axis=0), 1))
    score = tf * idf * spread
    # A label term must be shared: in ≥2 papers and ≥5% of the cluster's papers.
    shared = papers_with >= np.maximum(2, np.ceil(0.05 * sizes))[:, None]
    score = np.where(shared, score, 0)
    out = {}
    for row, c in enumerate(ids):
        order = np.argsort(-score[row])
        picked: list[tuple[str, float]] = []
        for j in order:
            term = terms[j]
            # Skip a unigram already covered by a chosen bigram, and vice versa.
            if score[row, j] <= 0:
                break
            # Variants of query words ("neuroligin-3") are as uninformative as the words.
            if any(w in term for w in exclude if len(w) >= 4):
                continue
            if any(term in t or t in term for t, _ in picked):
                continue
            picked.append((term, round(float(score[row, j]) * 1000, 3)))
            if len(picked) == top:
                break
        out[c] = picked
    return out


def build_text_map(
    papers: list[Paper],
    p: SemanticParams,
    query: str = "",
    embed: Embedder | None = None,
    progress: Callable[[str], None] | None = None,
) -> tuple[dict, dict]:
    """Returns (semantic_json, summary)."""
    report = progress or (lambda _m: None)
    usable = [x for x in papers if paper_text(x)]
    title_only = sum(1 for x in usable if not x.abstract.strip())
    summary: dict = {
        "papers": len(usable),
        "title_only": title_only,
        "skipped_no_text": len(papers) - len(usable),
    }
    if len(usable) < max(10, p.min_cluster_size * 2):
        summary["skipped"] = "Too few papers with text for a text map"
        return {"papers": [], "clusters": [], "edges": []}, summary

    report("Embedding titles and abstracts")
    texts = [paper_text(x) for x in usable]
    e = _normalize((embed or load_embedder(p.model))(texts))
    summary["dimensions"] = int(e.shape[1])

    report("Clustering papers by text")
    idx, sims = _knn(e, p.neighbors)
    labels = _clusters(idx, sims, p)
    n_clusters = int(labels.max() + 1) if (labels >= 0).any() else 0
    summary["clusters"] = n_clusters
    summary["unclustered"] = int((labels < 0).sum())
    clustered = labels >= 0
    if n_clusters >= 2:
        summary["silhouette"] = round(
            float(silhouette_score(e[clustered], labels[clustered], metric="cosine")), 3
        )

    report("Naming text clusters")
    excluded = query_words(query)
    terms = _ctfidf(texts, labels, exclude=excluded)
    summary["label_terms_excluded"] = sorted(w for w in excluded if w in query.lower())

    report("Projecting to 2D")
    reduced = PCA(n_components=min(50, e.shape[1], len(e) - 1), random_state=p.seed).fit_transform(
        e
    )
    perplexity = float(min(p.perplexity, (len(e) - 1) / 3))
    runs = []
    # The reference run starts from PCA (better global structure); the others start
    # from random positions, so stability measures sensitivity to initialization.
    for i in range(max(1, p.stability_runs)):
        y = TSNE(
            perplexity=perplexity,
            init="pca" if i == 0 else "random",
            random_state=p.seed + i,
            metric="cosine",
        ).fit_transform(reduced)
        y = y - y.mean(axis=0)
        runs.append(y / (np.sqrt((y**2).sum(axis=1).mean()) or 1))
    stability = _local_stability(runs, p.neighbors)
    xy = to_display(runs[0])

    # Neighborhood preservation: of each paper's k nearest papers by text, how many are
    # also among its k nearest on the map.
    k = idx.shape[1]
    map_nb = _map_neighbors(runs[0], k)
    kept = np.array([len(set(a) & set(b)) for a, b in zip(idx, map_nb, strict=True)])
    summary.update(
        {
            "neighbors_compared": k,
            "median_neighbors_kept": float(np.median(kept)),
            "median_stability": round(float(np.median(stability)), 3),
            "layout_runs": len(runs),
            "perplexity": round(perplexity, 1),
        }
    )

    pmids = [x.pmid for x in usable]
    out_papers = [
        {
            "pmid": pmids[i],
            "x": round(float(xy[i, 0]), 5),
            "y": round(float(xy[i, 1]), 5),
            "cluster": int(labels[i]),
            "stability": round(float(stability[i]), 3),
            "neighbors": [pmids[j] for j in idx[i]],
            "similarity": [round(float(s), 3) for s in sims[i]],
            "neighbors_kept": int(kept[i]),
            "title_only": not usable[i].abstract.strip(),
        }
        for i in range(len(usable))
    ]
    edges = {}
    for i in range(len(usable)):
        for j, s in zip(idx[i], sims[i], strict=True):
            a, b = sorted((pmids[i], pmids[int(j)]))
            edges[(a, b)] = max(edges.get((a, b), 0.0), float(s))
    clusters = []
    for c in range(n_clusters):
        members = np.where(labels == c)[0]
        centroid = _normalize(e[members].mean(axis=0, keepdims=True))[0]
        central = members[np.argsort(-(e[members] @ centroid))[:5]]
        top_terms = terms.get(c, [])
        clusters.append(
            {
                "id": c,
                "size": int(len(members)),
                "label": " · ".join(t for t, _ in top_terms[:3]) or f"Cluster {c + 1}",
                "terms": top_terms,
                "representative": [pmids[i] for i in central],
            }
        )
    return {
        "papers": out_papers,
        "clusters": clusters,
        "edges": [
            {"source": a, "target": b, "similarity": round(s, 3)} for (a, b), s in edges.items()
        ],
    }, summary
