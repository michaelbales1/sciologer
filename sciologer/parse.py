"""Parse PubMed efetch XML into Paper records."""

from __future__ import annotations

import re
from collections.abc import Iterable

from lxml import etree

from .records import Author, Grant, MeshTerm, Paper, Substance

_YEAR = re.compile(r"(1[89]\d\d|20\d\d)")


def _text(el: etree._Element | None) -> str:
    """All text in an element, including inline markup such as <i> and <sup>."""
    if el is None:
        return ""
    return " ".join("".join(el.itertext()).split())


def _year(article: etree._Element, citation: etree._Element) -> int | None:
    for path in (
        "Journal/JournalIssue/PubDate/Year",
        "ArticleDate/Year",
    ):
        value = article.findtext(path)
        if value and value.isdigit():
            return int(value)
    for value in (
        article.findtext("Journal/JournalIssue/PubDate/MedlineDate"),
        citation.findtext("DateCompleted/Year"),
    ):
        if value and (m := _YEAR.search(value)):
            return int(m.group(1))
    return None


def _author(el: etree._Element) -> Author:
    orcid = None
    for ident in el.findall("Identifier"):
        if (ident.get("Source") or "").upper() == "ORCID" and ident.text:
            orcid = ident.text.strip().rsplit("/", 1)[-1]
    return Author(
        last_name=el.findtext("LastName"),
        fore_name=el.findtext("ForeName"),
        initials=el.findtext("Initials"),
        collective_name=_text(el.find("CollectiveName")) or None,
        orcid=orcid,
        affiliations=[_text(a) for a in el.findall("AffiliationInfo/Affiliation") if _text(a)],
    )


def parse_article(node: etree._Element) -> Paper | None:
    citation = node.find("MedlineCitation")
    if citation is None:
        return None
    article = citation.find("Article")
    pmid = citation.findtext("PMID")
    if article is None or not pmid:
        return None

    doi = None
    for el in node.findall("PubmedData/ArticleIdList/ArticleId"):
        if el.get("IdType") == "doi" and el.text:
            doi = el.text.strip()
    if doi is None:
        for el in article.findall("ELocationID"):
            if el.get("EIdType") == "doi" and el.text:
                doi = el.text.strip()

    abstract_parts = []
    for el in article.findall("Abstract/AbstractText"):
        label, text = el.get("Label"), _text(el)
        if text:
            abstract_parts.append(f"{label}: {text}" if label else text)

    mesh = []
    for heading in citation.findall("MeshHeadingList/MeshHeading"):
        desc = heading.find("DescriptorName")
        if desc is None or not desc.get("UI"):
            continue
        major = desc.get("MajorTopicYN") == "Y" or any(
            q.get("MajorTopicYN") == "Y" for q in heading.findall("QualifierName")
        )
        mesh.append(MeshTerm(ui=desc.get("UI"), name=_text(desc), major=major))

    return Paper(
        pmid=pmid.strip(),
        title=_text(article.find("ArticleTitle")),
        year=_year(article, citation),
        journal=_text(article.find("Journal/Title")) or None,
        journal_abbrev=article.findtext("Journal/ISOAbbreviation"),
        doi=doi,
        abstract="\n".join(abstract_parts),
        authors=[_author(a) for a in article.findall("AuthorList/Author")],
        mesh=mesh,
        substances=[
            Substance(ui=s.get("UI"), name=_text(s))
            for s in citation.findall("ChemicalList/Chemical/NameOfSubstance")
            if _text(s)
        ],
        grants=[
            Grant(
                grant_id=g.findtext("GrantID"),
                agency=g.findtext("Agency"),
                country=g.findtext("Country"),
            )
            for g in article.findall("GrantList/Grant")
        ],
        keywords=[_text(k) for k in citation.findall("KeywordList/Keyword") if _text(k)],
        publication_types=[
            _text(t) for t in article.findall("PublicationTypeList/PublicationType")
        ],
    )


def parse_batches(batches: Iterable[bytes]) -> list[Paper]:
    """Parse efetch XML batches, dropping duplicate PMIDs (first occurrence wins)."""
    papers: dict[str, Paper] = {}
    parser = etree.XMLParser(resolve_entities=False, no_network=True, huge_tree=True)
    for batch in batches:
        root = etree.fromstring(batch, parser)
        for node in root.iter("PubmedArticle"):
            paper = parse_article(node)
            if paper and paper.pmid not in papers:
                papers[paper.pmid] = paper
    return list(papers.values())
