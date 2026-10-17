import json
from pathlib import Path

import httpx
import pytest

from sciologer import citations as cit
from sciologer.citations import CitationParams, ICiteClient, build_citation_map
from sciologer.parse import parse_batches
from sciologer.pipeline import RunParams, run_pipeline
from sciologer.pubmed import SearchResult
from sciologer.records import Paper

FIXTURES = Path(__file__).parent / "fixtures"


def papers():
    return parse_batches([(FIXTURES / "neuroligins.xml").read_bytes()])


def records():
    return json.loads((FIXTURES / "neuroligins-icite.json").read_text())


def test_citation_map_links_clusters_and_metrics():
    ps, rec = papers(), records()
    out, summary = build_citation_map(ps, rec, CitationParams(), query="neuroligins")
    pmids = {p["pmid"] for p in out["papers"]}
    assert summary["found_in_icite"] == len(rec)
    assert summary["mapped"] == len(out["papers"]) > 10
    # Every drawn paper has at least one link; links stay inside the set.
    linked = {e["source"] for e in out["edges"]} | {e["target"] for e in out["edges"]}
    assert pmids == linked
    for e in out["edges"]:
        assert e["direct"] or e["shared"] > 0
        assert e["weight"] >= (1.0 if e["direct"] else 0.0)
    # A direct link means one of the pair lists the other among its references.
    refs = {p: set(map(str, rec[p].get("references") or [])) for p in rec}
    for e in out["edges"]:
        if e["direct"]:
            assert e["target"] in refs[e["source"]] or e["source"] in refs[e["target"]]
    for p in out["papers"]:
        assert set(p["cites"]) <= pmids and set(p["cited_by"]) <= pmids
        assert 0 <= p["stability"] <= 1
        assert all(0 < c["cosine"] <= 1 for c in p["coupled"])
    assert out["layout_method"] in ("forceatlas2", "spectral_tsne")
    assert set(summary["layout"]["cluster_agreement"]) == {"forceatlas2", "spectral_tsne"}
    for c in out["clusters"]:
        assert c["size"] >= 3 and c["representative"]
        assert not any("neuroligin" in t for t, _ in c["terms"])


def test_coupling_backbone_keeps_each_papers_strongest_links():
    # Six papers all sharing one reference, plus a pair sharing many more.
    ps = [Paper(pmid=str(i), title=f"Paper {i}") for i in range(1, 7)]
    common = ["900"]
    rec = {str(i): {"pmid": i, "references": common + [str(100 + i)]} for i in range(1, 7)}
    rec["1"]["references"] += ["500", "501", "502"]
    rec["2"]["references"] += ["500", "501", "502"]
    out, summary = build_citation_map(ps, rec, CitationParams(coupling_neighbors=1))
    edges = {(e["source"], e["target"]) for e in out["edges"]}
    assert ("1", "2") in edges  # the strongest pair survives
    assert summary["coupled_pairs"] == 15  # every pair shares "900"
    assert summary["coupling_links_kept"] < 15  # but only the backbone is kept


def test_too_few_linked_papers_is_skipped():
    ps = [Paper(pmid=str(i), title="x") for i in range(3)]
    out, summary = build_citation_map(ps, {}, CitationParams())
    assert "skipped" in summary and out["papers"] == []


# --- the iCite client (the autouse fixture in conftest.py replaces fetch, so these
#     tests call the original through the module) ------------------------------------

ORIGINAL_FETCH = cit.ICiteClient.fetch


@pytest.fixture
def real_fetch(monkeypatch):
    monkeypatch.setattr(ICiteClient, "fetch", ORIGINAL_FETCH)
    cit._LIMITER._next = 0.0


def test_client_batches_retries_and_caches(tmp_path, real_fetch):
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        ids = request.url.params["pmids"].split(",")
        calls.append(len(ids))
        if len(calls) == 1:
            return httpx.Response(503)
        return httpx.Response(200, json={"data": [{"pmid": int(i)} for i in ids]})

    client = ICiteClient(
        cache_dir=tmp_path, transport=httpx.MockTransport(handler), sleep=lambda s: None
    )
    pmids = [str(i) for i in range(1, 451)]
    got = client.fetch(pmids)
    assert len(got) == 450 and client.retries == 1
    # URLs stay short: no request carries more than 200 PMIDs.
    assert max(calls) <= 200 and sum(calls[1:]) == 450
    n = len(calls)
    again = ICiteClient(cache_dir=tmp_path, transport=httpx.MockTransport(handler)).fetch(pmids)
    assert again == got and len(calls) == n


def test_pipeline_records_citation_step_and_survives_icite_outage(monkeypatch):
    xml = (FIXTURES / "neuroligins.xml").read_bytes()
    sr = SearchResult(
        query="neuroligins",
        count=80,
        pmids=[str(i) for i in range(80)],
        query_translation=None,
        xml_batches=[xml],
    )
    params = RunParams(query="neuroligins", text_map=False)
    params.layout.max_iter = 60
    params.layout.stability_runs = 2
    out = run_pipeline(params, "t", search_result=sr)
    assert out.citations and "Citation map" in [s.name for s in out.manifest.steps]

    def down(self, pmids, use_cache=True):
        raise httpx.ConnectError("iCite unreachable")

    monkeypatch.setattr(ICiteClient, "fetch", down)
    out = run_pipeline(params, "t", search_result=sr)
    assert out.citations is None
    assert any("iCite" in w for w in out.manifest.warnings)
