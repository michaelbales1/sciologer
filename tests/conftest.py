"""Keep the test suite offline: iCite lookups are served from a recorded fixture."""

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
