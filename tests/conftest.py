"""Keep the test suite offline: iCite and ROR lookups are served from recorded fixtures."""

import json
from pathlib import Path

import pytest

from sciologer.citations import ICiteClient

ICITE_FIXTURE = Path(__file__).parent / "fixtures" / "neuroligins-icite.json"


@pytest.fixture(autouse=True)
def offline_icite(monkeypatch):
    records = json.loads(ICITE_FIXTURE.read_text())

    def fetch(self, pmids, use_cache=True):
        self.retrieved_at = "2026-10-17T00:00:00+00:00"
        return {p: records[p] for p in pmids if p in records}

    monkeypatch.setattr(ICiteClient, "fetch", fetch)


ROR_FIXTURE = Path(__file__).parent / "fixtures" / "neuroligins-ror.json"


@pytest.fixture(autouse=True)
def offline_ror(monkeypatch):
    """ROR matches recorded for the neuroligins fixture's affiliations."""
    from sciologer.geo import RORClient

    matches = json.loads(ROR_FIXTURE.read_text())

    def match(self, affiliation):
        self.from_cache += 1
        return matches.get(affiliation)

    monkeypatch.setattr(RORClient, "match", match)
    monkeypatch.setattr(RORClient, "save", lambda self: None)
