"""Geographic view: ROR matching, the cache, and the institution network."""

import json
from pathlib import Path

import httpx

from sciologer.geo import GeoParams, RORClient, build_geo_map, clean_affiliation
from sciologer.parse import parse_batches
from sciologer.records import Author, Paper

FIXTURES = Path(__file__).parent / "fixtures"

ITEM = {
    "chosen": True,
    "score": 1.0,
    "organization": {
        "id": "https://ror.org/00hj8s172",
        "names": [{"value": "Columbia University", "types": ["ror_display", "label"]}],
        "types": ["education"],
        "locations": [
            {
                "geonames_details": {
                    "name": "New York",
                    "country_code": "US",
                    "country_name": "United States",
                    "country_subdivision_name": "New York",
                    "lat": 40.71,
                    "lng": -74.01,
                }
            }
        ],
    },
}


def test_clean_affiliation():
    assert clean_affiliation(
        "Dept X,  Columbia University, New York, NY, USA. jane@cumc.columbia.edu"
    ) == ("Dept X, Columbia University, New York, NY, USA")
    assert clean_affiliation("Hospital, Spain. Electronic address: https://x.org/a") == (
        "Hospital, Spain"
    )


def test_client_keeps_only_chosen_matches_and_caches_every_answer(tmp_path, monkeypatch):
    calls = []

    def handler(request):
        aff = request.url.params["affiliation"]
        calls.append(aff)
        if len(calls) == 1:
            return httpx.Response(429, headers={"Retry-After": "1"})
        items = [ITEM] if "Columbia" in aff else [{**ITEM, "chosen": False}]
        return httpx.Response(200, json={"items": items})

    # The suite serves ROR offline; this test exercises the real client.
    monkeypatch.undo()
    client = RORClient(
        cache_dir=tmp_path, transport=httpx.MockTransport(handler), sleep=lambda s: None
    )
    out = client.match_all(["Columbia University, New York", "Somewhere vague"], threads=1)
    assert out["Columbia University, New York"]["name"] == "Columbia University"
    assert out["Columbia University, New York"]["lat"] == 40.71
    assert out["Somewhere vague"] is None  # ROR wasn't confident: not placed
    assert client.retries == 1 and len(calls) == 3
    saved = json.loads((tmp_path / "ror-affiliations.json").read_text())
    assert set(saved) == {"Columbia University, New York", "Somewhere vague"}

    again = RORClient(cache_dir=tmp_path, transport=httpx.MockTransport(handler))
    assert again.match("Somewhere vague") is None and again.from_cache == 1
    assert len(calls) == 3  # both answers came from the cache


def test_geo_map_merges_spellings_and_links_institutions():
    papers = parse_batches([(FIXTURES / "neuroligins.xml").read_bytes()])
    data, summary = build_geo_map(papers, GeoParams(), RORClient())
    assert summary["papers_located"] > 0.7 * summary["papers"]
    assert summary["matched_affiliations"] < summary["distinct_affiliations"]
    nodes = {n["id"]: n for n in data["institutions"]}
    assert all(-90 <= n["lat"] <= 90 and -180 <= n["lng"] <= 180 for n in nodes.values())
    # Several spellings resolve to one institution.
    assert any(len(n["variants"]) > 1 for n in nodes.values())
    counts = [n["count"] for n in data["institutions"]]
    assert counts == sorted(counts, reverse=True)
    for e in data["edges"]:
        shared = set(nodes[e["source"]]["pmids"]) & set(nodes[e["target"]]["pmids"])
        assert e["count"] == len(shared) > 0
    assert sum(c["institutions"] for c in data["countries"]) == summary["institutions"]


def test_geo_map_caps_lookups_and_institutions():
    papers = parse_batches([(FIXTURES / "neuroligins.xml").read_bytes()])
    data, summary = build_geo_map(
        papers, GeoParams(max_lookups=20, max_institutions=10), RORClient()
    )
    assert summary["looked_up"] == 20 and summary["not_looked_up"] > 0
    assert len(data["institutions"]) <= 10


def test_no_affiliations_skips_the_view():
    papers = [Paper(pmid="1", authors=[Author(last_name="Lee", fore_name="Ann")])]
    data, summary = build_geo_map(papers, GeoParams(), RORClient())
    assert data is None and "skipped" in summary
