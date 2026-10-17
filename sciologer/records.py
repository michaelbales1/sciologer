"""Typed records parsed from PubMed."""

from __future__ import annotations

from enum import StrEnum

from pydantic import BaseModel, Field


class EntityType(StrEnum):
    """Kinds of nodes on the map. Mirrors the entity types of the 2008 Sciologer."""

    AUTHOR = "author"
    MESH = "mesh"
    JOURNAL = "journal"
    INSTITUTION = "institution"
    GRANT = "grant"
    SUBSTANCE = "substance"
    KEYWORD = "keyword"


class Author(BaseModel):
    last_name: str | None = None
    fore_name: str | None = None
    initials: str | None = None
    collective_name: str | None = None
    orcid: str | None = None
    affiliations: list[str] = Field(default_factory=list)

    @property
    def display_name(self) -> str:
        if self.collective_name:
            return self.collective_name
        return " ".join(p for p in (self.fore_name, self.last_name) if p) or "(unnamed)"


class MeshTerm(BaseModel):
    ui: str
    name: str
    major: bool = False


class Substance(BaseModel):
    ui: str | None = None
    name: str


class Grant(BaseModel):
    grant_id: str | None = None
    agency: str | None = None
    country: str | None = None


class Paper(BaseModel):
    pmid: str
    title: str = ""
    year: int | None = None
    journal: str | None = None
    journal_abbrev: str | None = None
    doi: str | None = None
    abstract: str = ""
    authors: list[Author] = Field(default_factory=list)
    mesh: list[MeshTerm] = Field(default_factory=list)
    substances: list[Substance] = Field(default_factory=list)
    grants: list[Grant] = Field(default_factory=list)
    keywords: list[str] = Field(default_factory=list)
    publication_types: list[str] = Field(default_factory=list)

    @property
    def url(self) -> str:
        return f"https://pubmed.ncbi.nlm.nih.gov/{self.pmid}/"
