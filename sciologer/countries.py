"""Country detection from free-text PubMed affiliations.

Affiliations usually end with the country ("…, Boston, MA, USA."), but the spelling
varies (USA / United States / U.S.A., UK / England, P.R. China, Republic of Korea), and
many US affiliations end with a state instead. We look at the comma-separated parts
from the end and return the first that names a country, as an ISO 3166 alpha-3 code.
Unrecognized affiliations return None and are reported as not covered rather than
guessed.
"""

from __future__ import annotations

import re
import unicodedata
from functools import lru_cache

import pycountry

US_STATES = {
    "alabama": "AL",
    "alaska": "AK",
    "arizona": "AZ",
    "arkansas": "AR",
    "california": "CA",
    "colorado": "CO",
    "connecticut": "CT",
    "delaware": "DE",
    "florida": "FL",
    "georgia": "GA",
    "hawaii": "HI",
    "idaho": "ID",
    "illinois": "IL",
    "indiana": "IN",
    "iowa": "IA",
    "kansas": "KS",
    "kentucky": "KY",
    "louisiana": "LA",
    "maine": "ME",
    "maryland": "MD",
    "massachusetts": "MA",
    "michigan": "MI",
    "minnesota": "MN",
    "mississippi": "MS",
    "missouri": "MO",
    "montana": "MT",
    "nebraska": "NE",
    "nevada": "NV",
    "new hampshire": "NH",
    "new jersey": "NJ",
    "new mexico": "NM",
    "new york": "NY",
    "north carolina": "NC",
    "north dakota": "ND",
    "ohio": "OH",
    "oklahoma": "OK",
    "oregon": "OR",
    "pennsylvania": "PA",
    "rhode island": "RI",
    "south carolina": "SC",
    "south dakota": "SD",
    "tennessee": "TN",
    "texas": "TX",
    "utah": "UT",
    "vermont": "VT",
    "virginia": "VA",
    "washington": "WA",
    "west virginia": "WV",
    "wisconsin": "WI",
    "wyoming": "WY",
    "district of columbia": "DC",
    "puerto rico": "PR",
}
# Two-letter codes that are also common words or other countries' codes are only
# trusted when followed by a ZIP code ("MA 02115").
_STATE_CODES = set(US_STATES.values())
# Common spellings in affiliations -> ISO alpha-3.
ALIASES = {
    "usa": "USA",
    "u s a": "USA",
    "us": "USA",
    "u s": "USA",
    "united states": "USA",
    "united states of america": "USA",
    "america": "USA",
    "uk": "GBR",
    "u k": "GBR",
    "united kingdom": "GBR",
    "england": "GBR",
    "scotland": "GBR",
    "wales": "GBR",
    "northern ireland": "GBR",
    "great britain": "GBR",
    "britain": "GBR",
    "china": "CHN",
    "p r china": "CHN",
    "pr china": "CHN",
    "peoples republic of china": "CHN",
    "people s republic of china": "CHN",
    "the people s republic of china": "CHN",
    "korea": "KOR",
    "south korea": "KOR",
    "republic of korea": "KOR",
    "korea republic of": "KOR",
    "korea south": "KOR",
    "north korea": "PRK",
    "taiwan": "TWN",
    "republic of china": "TWN",
    "taiwan roc": "TWN",
    "roc": "TWN",
    "hong kong": "HKG",
    "hong kong special administrative region": "HKG",
    "hong kong special administrative region of china": "HKG",
    "hong kong sar": "HKG",
    "hong kong china": "HKG",
    "macau": "MAC",
    "russia": "RUS",
    "russian federation": "RUS",
    "iran": "IRN",
    "islamic republic of iran": "IRN",
    "vietnam": "VNM",
    "viet nam": "VNM",
    "turkey": "TUR",
    "turkiye": "TUR",
    "czech republic": "CZE",
    "czechia": "CZE",
    "the netherlands": "NLD",
    "netherlands": "NLD",
    "holland": "NLD",
    "syria": "SYR",
    "tanzania": "TZA",
    "bolivia": "BOL",
    "venezuela": "VEN",
    "laos": "LAO",
    "moldova": "MDA",
    "palestine": "PSE",
    "state of palestine": "PSE",
    "uae": "ARE",
    "u a e": "ARE",
    "united arab emirates": "ARE",
    "ivory coast": "CIV",
    "cote d ivoire": "CIV",
    "brunei": "BRN",
    "cape verde": "CPV",
    "kosovo": "XKX",
    "saudi arabia": "SAU",
    "kingdom of saudi arabia": "SAU",
    "ksa": "SAU",
    "the gambia": "GMB",
    "gambia": "GMB",
    "democratic republic of the congo": "COD",
    "dr congo": "COD",
    "drc": "COD",
    "republic of the congo": "COG",
    "congo": "COG",
    "eswatini": "SWZ",
    "swaziland": "SWZ",
    "macedonia": "MKD",
    "north macedonia": "MKD",
}


def _fold(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c)).lower()
    return " ".join(re.sub(r"[^a-z0-9 ]", " ", text).split())


@lru_cache(maxsize=1)
def _names() -> dict[str, str]:
    names: dict[str, str] = {}
    for c in pycountry.countries:
        for attr in ("name", "official_name", "common_name"):
            v = getattr(c, attr, None)
            if v:
                names[_fold(v)] = c.alpha_3
    names.update(ALIASES)
    return names


# Contact details trail some affiliations: "… Spain. Electronic address: https://…"
_CONTACT = re.compile(r"electronic address:?.*$|\S+@\S+|https?://\S+", re.I)
_ZIP_STATE = re.compile(r"\b([A-Z]{2})\s+\d{5}(?:-\d{4})?\b")


@lru_cache(maxsize=50_000)
def country_of(affiliation: str) -> str | None:
    """ISO alpha-3 code of the country an affiliation names, or None."""
    text = _CONTACT.sub("", affiliation).strip(" .;")
    names = _names()
    parts = [p for p in re.split(r"[,;]", text) if p.strip()]
    for part in reversed(parts[-4:]):  # the country is almost always among the last parts
        f = _fold(part)
        if f in names:
            return names[f]
        # "Boston MA 02115", "Bethesda, Maryland 20892", "USA 10032"
        f_nozip = " ".join(w for w in f.split() if not w.isdigit())
        if f_nozip in names:
            return names[f_nozip]
        if f_nozip in US_STATES:
            return "USA"
        # A bare state code as its own part: "New Haven, CT"
        if part.strip(" .") in _STATE_CODES and part.strip(" .").isupper():
            return "USA"
        if _ZIP_STATE.search(part) and _ZIP_STATE.search(part).group(1) in _STATE_CODES:
            return "USA"
    # A country name at the very end without a comma ("… Toronto Canada").
    tail = _fold(text)
    for n in (4, 3, 2, 1):
        candidate = " ".join(tail.split()[-n:])
        if candidate in names and candidate not in ("us", "u s", "roc", "drc"):
            return names[candidate]
    return None
