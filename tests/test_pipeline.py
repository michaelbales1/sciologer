from pathlib import Path

import httpx
import pytest

from sciologer.cluster import ClusterParams, detect_clusters
from sciologer.layout import LayoutParams, compute_layout
from sciologer.network import NetworkParams, build_graph
from sciologer.normalize import (
    EntityTable,
    add_authors,
    add_grants,
    build_entities,
    grant_key,
    short_institution,
)
from sciologer.parse import parse_batches
from sciologer.pipeline import RunParams, run_pipeline
from sciologer.pubmed import _LIMITER, PubMedClient, RateLimitError, SearchResult
from sciologer.records import Author, EntityType, Grant, Paper

FIXTURES = Path(__file__).parent / "fixtures"


def load(name: str) -> list[Paper]:
    return parse_batches([(FIXTURES / f"{name}.xml").read_bytes()])


# --- parsing ---------------------------------------------------------------------


def test_parse_fixture_fields():
    papers = load("neuroligins")
    assert len(papers) == 80
    assert all(p.pmid.isdigit() for p in papers)
    assert sum(1 for p in papers if p.year) == 80
    assert any(p.mesh for p in papers)
    assert any(a.affiliations for p in papers for a in p.authors)
    assert len({p.pmid for p in papers}) == len(papers)


def test_parse_drops_duplicate_pmids():
    xml = (FIXTURES / "f1275.xml").read_bytes()
    assert len(parse_batches([xml, xml])) == len(parse_batches([xml]))


# --- normalization ---------------------------------------------------------------


def paper(pmid: str, *authors: Author, grants=()) -> Paper:
    return Paper(pmid=pmid, authors=list(authors), grants=list(grants))


def test_author_initial_merges_into_single_full_name_and_is_flagged():
    table = EntityTable()
    add_authors(
        [
            paper("1", Author(last_name="Südhof", fore_name="Thomas C")),
            paper("2", Author(last_name="Sudhof", fore_name="T", initials="T")),
        ],
        table,
    )
    authors = [e for e in table.entities.values() if e.type == EntityType.AUTHOR]
    assert len(authors) == 1 and authors[0].pmids == {"1", "2"}
    assert any(e.action == "merged" and e.uncertain for e in table.log)


def test_author_initial_matching_two_names_stays_separate():
    table = EntityTable()
    add_authors(
        [
            paper("1", Author(last_name="Smith", fore_name="John")),
            paper("2", Author(last_name="Smith", fore_name="James")),
            paper("3", Author(last_name="Smith", fore_name="J")),
        ],
        table,
    )
    assert len(table.entities) == 3
    assert any(e.action == "kept_separate" and e.uncertain for e in table.log)


def test_orcid_keys_author():
    table = EntityTable()
    add_authors([paper("1", Author(last_name="Ng", fore_name="Ann", orcid="0000-0001"))], table)
    assert "author:orcid:0000-0001" in table.entities


@pytest.mark.parametrize(
    ("raw", "key", "full"),
    [
        ("R01 MH067947", "R01MH067947", True),
        ("5R01MH067947-02", "R01MH067947", True),
        ("KO1-MH105040-01", "K01MH105040", True),
        ("MH067947", "MH067947", False),
    ],
)
def test_grant_key(raw, key, full):
    assert grant_key(raw) == (key, full)


def test_grants_keep_different_activity_codes_apart_and_split_lists():
    table = EntityTable()
    add_grants(
        [
            paper("1", grants=[Grant(grant_id="F32 MH105040")]),
            paper("2", grants=[Grant(grant_id="K01-MH105040")]),
            paper("3", grants=[Grant(grant_id="P50CA165962, R01CA258384")]),
        ],
        table,
    )
    assert {
        "grant:F32MH105040",
        "grant:K01MH105040",
        "grant:P50CA165962",
        "grant:R01CA258384",
    } <= set(table.entities)


def test_short_institution_prefers_university_and_strips_email():
    name, ok = short_institution(
        "Department of Neuroscience, Center for Brain Science, Harvard University, "
        "Cambridge, MA, USA. jdoe@harvard.edu"
    )
    assert (name, ok) == ("Harvard University", True)
    assert short_institution("Lifera Omics, Riyadh")[1] is False


def test_check_tags_excluded_and_logged():
    papers = load("neuroligins")
    table = build_entities(papers, [EntityType.MESH])
    assert not any(e.label in {"Humans", "Animals", "Male"} for e in table.entities.values())
    assert any(e.action == "excluded" for e in table.log)


# --- network, clusters, layout ---------------------------------------------------


def test_network_respects_min_occurrences_and_per_type_cap():
    papers = load("neuroligins")
    table = build_entities(papers, [EntityType.AUTHOR, EntityType.MESH])
    g, summary = build_graph(table, NetworkParams(min_occurrences=3, max_per_type=10))
    assert all(d["count"] >= 3 for _, d in g.nodes(data=True))
    assert summary["mesh"]["shown"] <= 10 and summary["author"]["shown"] <= 10
    assert all(d["weight"] > 0 for *_, d in g.edges(data=True))


def test_layout_and_clusters_are_deterministic():
    papers = load("neuroligins")
    table = build_entities(papers, [EntityType.MESH, EntityType.SUBSTANCE])

    def run():
        g, _ = build_graph(table, NetworkParams())
        detect_clusters(g, ClusterParams())
        compute_layout(g, LayoutParams(max_iter=60, stability_runs=3))
        return {n: (d["x"], d["y"], d["cluster"], d["stability"]) for n, d in g.nodes(data=True)}

    a, b = run(), run()
    assert a == b
    assert all(abs(x) <= 1.05 and abs(y) <= 1.05 and 0 <= s <= 1 for x, y, _, s in a.values())


def test_pipeline_end_to_end_from_fixture():
    xml = (FIXTURES / "neuroligins.xml").read_bytes()
    sr = SearchResult(
        query="neuroligins",
        count=1294,
        pmids=[str(i) for i in range(80)],
        query_translation="neuroligins[All Fields]",
        xml_batches=[xml],
    )
    out = run_pipeline(
        RunParams(
            query="neuroligins",
            layout=LayoutParams(max_iter=60, stability_runs=2),
            text_map=False,  # covered in test_semantic.py with an offline embedder
            citation_map=False,  # covered in test_citations.py
        ),
        "test",
        search_result=sr,
    )
    m = out.manifest
    assert [s.name for s in m.steps] == [
        "Retrieve",
        "Parse",
        "Normalize",
        "Network",
        "Cluster",
        "Layout",
        "Geography",
    ]
    assert out.geo and out.geo["institutions"]
    assert m.pubmed_count == 1294 and m.fetched == 80 and m.parsed == 80
    assert any("first 80" in w for w in m.warnings)
    node_ids = {n["id"] for n in out.graph["nodes"]}
    assert all(e["source"] in node_ids and e["target"] in node_ids for e in out.graph["edges"])
    assert all(pmid in out.papers for n in out.graph["nodes"] for pmid in n["pmids"])


# --- PubMed client: rate limits and cache (no network) ----------------------------

ESEARCH = (
    b"<eSearchResult><Count>1</Count><IdList><Id>1</Id></IdList>"
    b"<QueryKey>1</QueryKey><WebEnv>X</WebEnv></eSearchResult>"
)


@pytest.fixture(autouse=True)
def _reset_limiter():
    _LIMITER.reset()
    yield
    _LIMITER.reset()


def test_client_retries_after_429_and_honors_retry_after(tmp_path):
    calls, sleeps = [], []
    fixture = (FIXTURES / "f1275.xml").read_bytes()

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.path)
        if len(calls) == 1:
            return httpx.Response(429, headers={"Retry-After": "2"})
        if request.url.path.endswith("esearch.fcgi"):
            return httpx.Response(200, content=ESEARCH)
        return httpx.Response(200, content=fixture)

    client = PubMedClient(
        api_key="k", cache_dir=tmp_path, transport=httpx.MockTransport(handler), sleep=sleeps.append
    )
    result = client.search("q", max_records=10)
    assert result.retries == 1 and sleeps[0] == pytest.approx(2.0, abs=0.05)
    assert len(calls) == 3

    again = client.search("q", max_records=10)
    assert again.from_cache and len(calls) == 3
    assert again.xml_batches == result.xml_batches


def test_client_gives_up_with_clear_error():
    client = PubMedClient(
        api_key="k",
        transport=httpx.MockTransport(lambda r: httpx.Response(429)),
        sleep=lambda s: None,
    )
    with pytest.raises(RateLimitError, match="NCBI_API_KEY"):
        client.search("q")


def test_substances_already_in_mesh_are_shown_once():
    papers = load("neuroligins")
    both = build_entities(papers, [EntityType.MESH, EntityType.SUBSTANCE])
    mesh_uis = {e.attrs.get("ui") for e in both.entities.values() if e.type == EntityType.MESH}
    sub_keys = {
        e.key.split(":", 1)[1] for e in both.entities.values() if e.type == EntityType.SUBSTANCE
    }
    assert not (mesh_uis & sub_keys)
    alone = build_entities(papers, [EntityType.SUBSTANCE])
    assert len(alone.entities) > len(sub_keys)


def test_cache_keeps_original_retrieval_time(tmp_path):
    fixture = (FIXTURES / "f1275.xml").read_bytes()

    def handler(request):
        if request.url.path.endswith("esearch.fcgi"):
            return httpx.Response(200, content=ESEARCH)
        return httpx.Response(200, content=fixture)

    client = PubMedClient(api_key="k", cache_dir=tmp_path, transport=httpx.MockTransport(handler))
    first = client.search("q", max_records=10)
    again = client.search("q", max_records=10)
    assert first.retrieved_at and again.retrieved_at == first.retrieved_at


def test_fractional_counting_ranks_and_links_authors():
    from sciologer.network import author_credit

    big = [Author(last_name=f"Member{i}", fore_name="A") for i in range(40)]
    lead = Author(last_name="Lead", fore_name="Pat")
    pal = Author(last_name="Pal", fore_name="Sam")
    papers = [paper(f"c{i}", lead, *big) for i in range(3)]  # consortium papers
    papers += [paper(f"s{i}", lead, pal) for i in range(2)]  # a regular collaborator
    authors_per_paper = {p.pmid: len(p.authors) for p in papers}
    table = build_entities(papers, [EntityType.AUTHOR])

    credit = author_credit(table, authors_per_paper)
    lead_key = next(k for k, e in table.entities.items() if e.label == "Pat Lead")
    pal_key = next(k for k, e in table.entities.items() if e.label == "Sam Pal")
    assert credit[lead_key] == pytest.approx(3 / 41 + 2 / 2)
    assert credit[pal_key] == pytest.approx(1.0)

    # With room for 5 authors, full counting fills the slots with consortium members
    # (3 papers each beat Sam Pal's 2); fractional counting keeps the real collaborator.
    full, _ = build_graph(
        table, NetworkParams(max_per_type=5, author_counting="full"), authors_per_paper
    )
    frac, summary = build_graph(
        table, NetworkParams(max_per_type=5, author_counting="fractional"), authors_per_paper
    )
    assert pal_key not in full and pal_key in frac
    assert frac.nodes[pal_key]["credit"] == pytest.approx(1.0)
    assert "fractional" in summary["author_counting"]["method"]
    # Author–author links from a 41-author paper count 1/40 each.
    member = next(k for k in frac if k not in (lead_key, pal_key))
    assert frac[lead_key][member]["counted"] == pytest.approx(3 / 40)
    assert frac[lead_key][pal_key]["count"] == 2


def test_leiden_clusters_are_connected_and_deterministic():
    import networkx as nx

    from sciologer.cluster import leiden_communities

    papers = load("neuroligins")
    table = build_entities(papers, [EntityType.MESH, EntityType.AUTHOR, EntityType.SUBSTANCE])
    g, _ = build_graph(table, NetworkParams(), {p.pmid: len(p.authors) for p in papers})
    comms = leiden_communities(g, "weight", 1.0, 42)
    assert comms == leiden_communities(g, "weight", 1.0, 42)
    assert set().union(*comms) == set(g.nodes)  # a partition: every node exactly once
    assert sum(len(c) for c in comms) == g.number_of_nodes()
    # Leiden's guarantee over Louvain: no community is internally disconnected.
    assert all(nx.is_connected(g.subgraph(c)) for c in comms if len(c) > 1)
    # Higher resolution gives at least as many clusters.
    assert len(leiden_communities(g, "weight", 2.0, 42)) >= len(comms)


def test_leiden_handles_edgeless_and_empty_graphs():
    import networkx as nx

    from sciologer.cluster import leiden_communities

    assert leiden_communities(nx.Graph()) == []
    g = nx.Graph()
    g.add_nodes_from(["a", "b"])
    assert sorted(map(sorted, leiden_communities(g))) == [["a"], ["b"]]
