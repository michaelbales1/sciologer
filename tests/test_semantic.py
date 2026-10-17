"""Text map tests. A tiny deterministic bag-of-words embedder keeps them offline."""

from pathlib import Path

import numpy as np
from sklearn.feature_extraction.text import HashingVectorizer

from sciologer.parse import parse_batches
from sciologer.pipeline import RunParams, run_pipeline
from sciologer.pubmed import SearchResult
from sciologer.semantic import SemanticParams, build_text_map, query_words

FIXTURES = Path(__file__).parent / "fixtures"
_HASH = HashingVectorizer(n_features=256, stop_words="english", alternate_sign=False)


def fake_embed(texts: list[str]) -> np.ndarray:
    return _HASH.transform(texts).toarray().astype(np.float32)


def papers():
    return parse_batches([(FIXTURES / "neuroligins.xml").read_bytes()])


def test_text_map_structure_and_evidence():
    out, summary = build_text_map(
        papers(), SemanticParams(stability_runs=2), query="neuroligins", embed=fake_embed
    )
    pmids = {p.pmid for p in papers()}
    assert summary["papers"] == len(out["papers"]) == 80
    assert summary["clusters"] == len(out["clusters"]) >= 2
    for p in out["papers"]:
        assert abs(p["x"]) <= 1.05 and abs(p["y"]) <= 1.05
        assert 0 <= p["stability"] <= 1
        assert len(p["neighbors"]) == 10 and set(p["neighbors"]) <= pmids
        assert p["pmid"] not in p["neighbors"]
        assert 0 <= p["neighbors_kept"] <= 10
    sizes = [c["size"] for c in out["clusters"]]
    assert sizes == sorted(sizes, reverse=True) and min(sizes) >= 5
    for c in out["clusters"]:
        assert c["terms"] and c["representative"]
        # The query's own words never name a cluster.
        assert not any("neuroligin" in t for t, _ in c["terms"])


def test_text_map_is_deterministic():
    a, _ = build_text_map(papers(), SemanticParams(stability_runs=2), embed=fake_embed)
    b, _ = build_text_map(papers(), SemanticParams(stability_runs=2), embed=fake_embed)
    assert a == b


def test_too_few_papers_is_skipped_not_crashed():
    out, summary = build_text_map(papers()[:5], SemanticParams(), embed=fake_embed)
    assert "skipped" in summary and out["papers"] == []


def test_query_words_ignore_field_tags_and_operators():
    assert {"neuroligin", "neuroligins", "autism"} <= query_words(
        'neuroligins[MeSH Terms] AND "autism"'
    )
    assert "mesh" not in query_words("neuroligins[MeSH Terms]")
    assert "and" not in query_words("a AND b")


def test_pipeline_adds_text_map_step():
    xml = (FIXTURES / "neuroligins.xml").read_bytes()
    sr = SearchResult(
        query="neuroligins",
        count=80,
        pmids=[str(i) for i in range(80)],
        query_translation=None,
        xml_batches=[xml],
    )
    params = RunParams(query="neuroligins")
    params.layout.max_iter = 60
    params.layout.stability_runs = 2
    params.semantic.stability_runs = 2
    out = run_pipeline(params, "t", search_result=sr, embed=fake_embed)
    assert out.text and out.text["papers"]
    assert "Text map" in [s.name for s in out.manifest.steps]
    assert all(p["pmid"] in out.papers for p in out.text["papers"])
