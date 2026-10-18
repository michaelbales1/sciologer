import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from sciologer.api import create_app
from sciologer.pubmed import PubMedClient, SearchResult
from sciologer.runs import RunStore

FIXTURES = Path(__file__).parent / "fixtures"


@pytest.fixture
def client(tmp_path, monkeypatch):
    xml = (FIXTURES / "neuroligins.xml").read_bytes()

    def fake_search(self, query, max_records=1000, progress=None, use_cache=True):
        return SearchResult(
            query=query,
            count=1294,
            pmids=[str(i) for i in range(80)],
            query_translation=query,
            xml_batches=[xml],
        )

    monkeypatch.setattr(PubMedClient, "search", fake_search)
    monkeypatch.setattr(
        PubMedClient,
        "fetch_ids",
        lambda self, pmids, progress=None, use_cache=True: fake_search(self, "ids"),
    )
    from test_semantic import fake_embed

    monkeypatch.setattr("sciologer.semantic.load_embedder", lambda model: fake_embed)
    return TestClient(create_app(RunStore(tmp_path)))


def wait_done(client, run_id):
    for _ in range(300):
        status = client.get(f"/api/runs/{run_id}/status").json()
        if status["state"] in ("done", "error"):
            return status
        time.sleep(0.05)
    raise AssertionError("run did not finish")


def test_run_lifecycle_and_exports(client):
    defaults = client.get("/api/defaults").json()
    params = defaults["params"] | {"query": "neuroligins"}
    params["layout"]["stability_runs"] = 2
    params["layout"]["max_iter"] = 60
    params["semantic"]["stability_runs"] = 2
    started = client.post("/api/runs", json=params)
    assert started.status_code == 202
    run_id = started.json()["run_id"]

    status = wait_done(client, run_id)
    assert status["state"] == "done", status

    run = client.get(f"/api/runs/{run_id}").json()
    assert run["manifest"]["parsed"] == 80
    assert run["graph"]["nodes"]
    assert [r["run_id"] for r in client.get("/api/runs").json()] == [run_id]

    assert run["text"]["papers"]
    for fmt in ("graphml", "gexf", "nodes.csv", "edges.csv", "json", "text.csv"):
        resp = client.get(f"/api/runs/{run_id}/export/{fmt}")
        assert resp.status_code == 200, fmt
        assert "attachment" in resp.headers["content-disposition"]
    assert "<gexf" in client.get(f"/api/runs/{run_id}/export/gexf").text

    # Geography is off by default and can be added to the saved map later.
    assert run["geo"] is None
    assert client.get(f"/api/runs/{run_id}/geo/status").status_code == 404
    est = client.get(f"/api/runs/{run_id}/geo/estimate").json()
    assert est["distinct_affiliations"] > 0 and est["planned"] == est["distinct_affiliations"]
    assert est["seconds"] >= 0
    assert client.post(f"/api/runs/{run_id}/geo").status_code == 202
    for _ in range(300):
        status = client.get(f"/api/runs/{run_id}/geo/status").json()
        if status["state"] in ("done", "error"):
            break
        time.sleep(0.05)
    assert status["state"] == "done", status
    run = client.get(f"/api/runs/{run_id}").json()
    assert run["geo"]["institutions"]
    assert [s["name"] for s in run["manifest"]["steps"]].count("Geography") == 1
    csv = client.get(f"/api/runs/{run_id}/export/geo.csv").text
    assert csv.startswith("ror_id,name,city")
    assert client.post("/api/runs/20260101-000000-nope/geo").status_code == 404

    # Maps saved before affiliations were kept: records are fetched again by PMID.
    folder = client.app.state.store.path(run_id)
    (folder / "affiliations.json").unlink()
    again = client.get(f"/api/runs/{run_id}/geo/estimate").json()
    assert again["distinct_affiliations"] == est["distinct_affiliations"]
    assert (folder / "affiliations.json").exists()


def test_rejects_bad_input(client):
    assert client.post("/api/runs", json={"query": "  "}).status_code == 422
    assert client.post("/api/runs", json={"query": "x", "types": []}).status_code == 422
    too_many = client.post("/api/runs", json={"query": "x", "network": {"max_per_type": 600}})
    assert too_many.status_code == 422
    assert "max_per_type" in str(too_many.json()["detail"])
    assert client.get("/api/runs/..%2F..%2Fetc/status").status_code == 404
    assert client.get("/api/nope").status_code == 404
    assert client.get("/api/runs/20260101-000000-nope").status_code == 404
