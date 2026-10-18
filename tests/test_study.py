"""Batch studies, collaboration measures and complete retrieval. All offline."""

import csv
import json
import re
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from sciologer import pubmed as pm
from sciologer.api import create_app
from sciologer.metrics import METRIC_INFO, collaboration_metrics
from sciologer.parse import parse_batches
from sciologer.pubmed import PubMedClient, SearchResult
from sciologer.records import Author, Paper
from sciologer.runs import RunStore
from sciologer.study import Study, StudyStore, load_study, run_study

FIXTURES = Path(__file__).parent / "fixtures"
XML = (FIXTURES / "neuroligins.xml").read_bytes()


# --- collaboration measures ------------------------------------------------------


def paper(pmid, *names, aff=None):
    return Paper(
        pmid=pmid,
        authors=[
            Author(
                last_name=n.split()[1], fore_name=n.split()[0], affiliations=[aff] if aff else []
            )
            for n in names
        ],
    )


def test_metrics_on_a_known_network():
    papers = [
        paper("1", "Ann Lee", "Bo Kim", aff="Dept, Alpha University"),
        paper("2", "Ann Lee", "Bo Kim", "Cy Wu"),
        paper("3", "Di Ng"),  # single author, own component
    ]
    m = collaboration_metrics(papers)
    assert m["papers"] == 3 and m["authors"] == 4
    assert m["single_author_share"] == pytest.approx(1 / 3, abs=1e-4)
    assert m["network_edges"] == 3  # Lee-Kim, Lee-Wu, Kim-Wu
    assert m["components"] == 2
    assert m["largest_component_share"] == pytest.approx(0.75)
    assert m["repeat_collaboration_share"] == pytest.approx(1 / 3, abs=1e-4)  # Lee-Kim twice
    assert m["transitivity"] == pytest.approx(1.0)
    assert m["affiliation_coverage"] == pytest.approx(1 / 3, abs=1e-4)


def test_metrics_leave_out_hyper_authored_papers_from_the_network():
    big = paper("9", *[f"A{i} Member" for i in range(150)])
    small = paper("1", "Ann Lee", "Bo Kim")
    m = collaboration_metrics([big, small])
    assert m["network_papers_excluded"] == 1
    assert m["network_edges"] == 1
    assert m["large_team_share"] == pytest.approx(0.5)


def test_metrics_on_fixture_are_well_formed_and_deterministic():
    papers = parse_batches([XML])
    a, b = collaboration_metrics(papers), collaboration_metrics(papers)
    assert a == b
    assert set(a) == set(METRIC_INFO)
    for k, v in a.items():
        if v is not None and (k.endswith("_share") or k == "affiliation_coverage"):
            assert 0 <= v <= 1, k


# --- complete retrieval: splitting by date ---------------------------------------


def test_large_queries_split_into_date_ranges_under_the_limit(monkeypatch):
    monkeypatch.setattr(pm, "SEARCH_LIMIT", 100)
    # 1,000 papers spread over 2020-2024; one month alone has 150 (> limit).
    months = {(y, m): 15 for y in range(2020, 2025) for m in range(1, 13)}
    months[(2022, 3)] = 150
    total = sum(months.values())

    def fake_count(self, query):
        m = re.search(r"(\d{4})/(\d{2}):(\d{4})/(\d{2})\[dp\]", query)
        if not m:
            return total, "translated"
        lo = int(m[1]) * 12 + int(m[2]) - 1
        hi = int(m[3]) * 12 + int(m[4]) - 1
        return sum(n for (y, mo), n in months.items() if lo <= y * 12 + mo - 1 <= hi), None

    monkeypatch.setattr(PubMedClient, "count", fake_count)
    ranges = PubMedClient(api_key="k")._ranges("q")
    assert sum(n for _, n in ranges) == total
    over = [(q, n) for q, n in ranges if n > 100]
    assert len(over) == 1 and "2022/03:2022/03" in over[0][0]  # one month can't be split
    assert all(n <= 100 for q, n in ranges if q not in dict(over))


# --- batch studies ----------------------------------------------------------------


class FakePubMed:
    """Stands in for PubMedClient: every query returns the fixture's 80 papers."""

    def __init__(self, fail_on=None, count=80):
        self.calls = []
        self.fail_on = fail_on
        self.n = count
        self.ids = [p.pmid for p in parse_batches([XML])]

    def _result(self, query, pmids):
        return SearchResult(
            query=query, count=self.n, pmids=pmids, query_translation=query, xml_batches=[XML]
        )

    def count(self, query):
        self.calls.append(("count", query))
        if self.fail_on and self.fail_on in query:
            raise ValueError("PubMed rejected the query: bad syntax")
        return self.n, query

    def search_all(self, query, progress=None, use_cache=True):
        self.calls.append(("search_all", query))
        return self._result(query, self.ids)

    def ids_all(self, query):
        self.calls.append(("ids_all", query))
        return self.ids, self.n, query

    def fetch_ids(self, pmids, progress=None, use_cache=True):
        self.calls.append(("fetch_ids", len(pmids)))
        return self._result("ids", list(pmids))

    def close(self):
        pass


def study(**settings):
    return Study.model_validate(
        {
            "name": "Test study",
            "settings": {
                "views": ["entities", "citations"],
                **settings,
                "params": {"layout": {"max_iter": 40, "stability_runs": 2}},
            },
            "queries": [{"label": "A", "query": "alpha[TA]"}, "beta[TA]"],
        }
    )


def test_study_runs_freezes_resumes_and_writes_table(tmp_path):
    store = StudyStore(RunStore(tmp_path))
    fake = FakePubMed()
    status = run_study(study(), store, pubmed=fake, log=lambda m: None)
    assert [q["state"] for q in status["queries"]] == ["done", "done"]
    assert status["queries"][1]["label"] == "beta[TA]"  # plain string: query is the label
    folder = store.path("test-study")
    assert (folder / "pmids" / "a.txt").read_text().split() == fake.ids
    rows = list(csv.DictReader((folder / "metrics.csv").open()))
    assert [r["label"] for r in rows] == ["A", "beta[TA]"] and rows[0]["authors"]
    # Runs are saved and tagged with the study.
    run_id = status["queries"][0]["run_id"]
    manifest = json.loads((tmp_path / "runs" / run_id / "manifest.json").read_text())
    assert manifest["study"]["label"] == "A"

    # Rerun: everything is done, so nothing is fetched.
    fake.calls.clear()
    run_study(study(), store, pubmed=fake, log=lambda m: None)
    assert fake.calls == []

    # --refresh reuses nothing; without it a re-run of a query uses its frozen PMIDs.
    status["queries"][0]["state"] = "pending"
    (folder / "status.json").write_text(json.dumps(status))
    fake.calls.clear()
    run_study(study(), store, pubmed=fake, log=lambda m: None)
    assert fake.calls == [("fetch_ids", 80)]


def test_study_failure_is_recorded_and_others_continue(tmp_path):
    store = StudyStore(RunStore(tmp_path))
    status = run_study(study(), store, pubmed=FakePubMed(fail_on="alpha"), log=lambda m: None)
    assert status["queries"][0]["state"] == "error"
    assert "bad syntax" in status["queries"][0]["error"]
    assert status["queries"][1]["state"] == "done"


def test_large_sets_need_a_sample_and_samples_are_reproducible(tmp_path):
    store = StudyStore(RunStore(tmp_path))
    big = FakePubMed(count=50_000)
    status = run_study(study(), store, pubmed=big, log=lambda m: None)
    assert all(q["state"] == "error" and "sample" in q["error"] for q in status["queries"])

    store2 = StudyStore(RunStore(tmp_path / "b"))
    fake = FakePubMed(count=50_000)
    status = run_study(study(sample=30), store2, pubmed=fake, log=lambda m: None)
    q = status["queries"][0]
    assert q["state"] == "done" and q["sampled_from"] == 50_000 and q["retrieved"] == 30
    frozen = (store2.path("test-study") / "pmids" / "a.txt").read_text().split()
    store3 = StudyStore(RunStore(tmp_path / "c"))
    run_study(study(sample=30), store3, pubmed=FakePubMed(count=50_000), log=lambda m: None)
    assert (store3.path("test-study") / "pmids" / "a.txt").read_text().split() == frozen


def test_study_file_validation(tmp_path):
    f = tmp_path / "s.yaml"
    f.write_text("name: X\nqueries:\n  - {label: A, query: a}\n  - {label: A, query: b}\n")
    with pytest.raises(ValueError, match="unique"):
        load_study(f)
    f.write_text("name: X\nqueries: []\n")
    with pytest.raises(ValueError, match="at least one"):
        load_study(f)
    example = load_study(
        Path(__file__).parent.parent / "examples" / "informatics-journals-2021-2026.yaml"
    )
    assert [q.label for q in example.queries] == ["JBI", "JAMIA", "Scientometrics"]


def test_studies_api(tmp_path):
    runs = RunStore(tmp_path)
    run_study(study(), StudyStore(runs), pubmed=FakePubMed(), log=lambda m: None)
    client = TestClient(create_app(runs))
    listing = client.get("/api/studies").json()
    assert listing == [
        {
            "id": "test-study",
            "name": "Test study",
            "queries": 2,
            "done": 2,
            "failed": 0,
            "updated_at": listing[0]["updated_at"],
        }
    ]
    st = client.get("/api/studies/test-study").json()
    assert st["metric_info"]["density"]["label"] == "Density"
    assert client.get("/api/studies/test-study/metrics.csv").text.startswith("label,query")
    assert client.get("/api/studies/nope").status_code == 404
    assert client.get("/api/studies/..%2Fetc").status_code == 404
