"""Turn parsed papers into entities, recording every normalization decision.

Preprocessing is not neutral: a different matching rule changes counts and clusters
(Nowakowska 2025). So each merge is written to a log, and merges that rest on weak
evidence (an initial matched to a full first name, an institution that no rule
recognized) are flagged as uncertain so they can be inspected in the UI.
"""

from __future__ import annotations

import re
import unicodedata
from collections import defaultdict
from collections.abc import Iterable

from pydantic import BaseModel, Field

from .records import Author, EntityType, Paper


class Entity(BaseModel):
    key: str
    type: EntityType
    label: str
    pmids: set[str] = Field(default_factory=set)
    variants: set[str] = Field(default_factory=set)
    attrs: dict[str, str] = Field(default_factory=dict)

    @property
    def count(self) -> int:
        return len(self.pmids)


class LogEntry(BaseModel):
    type: EntityType
    action: str  # "merged", "kept_separate", "unparsed", "excluded"
    label: str
    variants: list[str]
    uncertain: bool
    reason: str


class EntityTable(BaseModel):
    entities: dict[str, Entity] = Field(default_factory=dict)
    log: list[LogEntry] = Field(default_factory=list)

    def add(
        self,
        type_: EntityType,
        ident: str,
        label: str,
        pmid: str,
        variant: str | None = None,
        **attrs: str,
    ) -> None:
        key = f"{type_.value}:{ident}"
        ent = self.entities.get(key)
        if ent is None:
            ent = self.entities[key] = Entity(key=key, type=type_, label=label, attrs=attrs)
        ent.pmids.add(pmid)
        ent.variants.add(variant or label)

    def by_paper(self) -> dict[str, set[str]]:
        out: dict[str, set[str]] = defaultdict(set)
        for ent in self.entities.values():
            for pmid in ent.pmids:
                out[pmid].add(ent.key)
        return out


def fold(text: str) -> str:
    """Lowercase, strip accents and punctuation, collapse whitespace."""
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = re.sub(r"[^\w\s]", " ", text.lower())
    return " ".join(text.split())


# --- Authors ---------------------------------------------------------------------


def _first_token(fore: str | None) -> str:
    toks = fold(fore or "").split()
    return toks[0] if toks else ""


def _compatible(a: str, b: str) -> bool:
    return a.startswith(b) or b.startswith(a)


def add_authors(papers: Iterable[Paper], table: EntityTable) -> None:
    """Authors are keyed by ORCID when present, otherwise by surname + first name.

    Name-only records whose first name is just an initial are merged into the single
    full name that matches it (flagged uncertain); if several full names match, they
    are kept separate and flagged as ambiguous.
    """
    # surname -> first-name token -> list of (pmid, author)
    by_name: dict[str, dict[str, list[tuple[str, Author]]]] = defaultdict(lambda: defaultdict(list))
    orcid_by_name: dict[tuple[str, str], set[str]] = defaultdict(set)
    for paper in papers:
        for au in paper.authors:
            if au.collective_name and not au.last_name:
                ident = "group:" + fold(au.collective_name)
                table.add(EntityType.AUTHOR, ident, au.collective_name, paper.pmid)
                continue
            last = fold(au.last_name or "")
            if not last:
                continue
            first = _first_token(au.fore_name) or fold(au.initials or "")[:1]
            if au.orcid:
                orcid_by_name[(last, first)].add(au.orcid)
            by_name[last][first].append((paper.pmid, au))

    for last, firsts in by_name.items():
        full = [f for f in firsts if len(f) > 1]
        target: dict[str, str] = {f: f for f in firsts}
        for f in firsts:
            if len(f) > 1:
                continue
            matches = [g for g in full if _compatible(g, f)]
            if len(matches) == 1:
                target[f] = matches[0]
            elif len(matches) > 1:
                labels = sorted({_label(a) for g in matches for _, a in firsts[g]})
                table.log.append(
                    LogEntry(
                        type=EntityType.AUTHOR,
                        action="kept_separate",
                        label=_label(firsts[f][0][1]),
                        variants=labels,
                        uncertain=True,
                        reason=f"Initial '{f.upper()}' matches several full first names",
                    )
                )

        groups: dict[str, list[tuple[str, Author]]] = defaultdict(list)
        for f, rows in firsts.items():
            groups[target[f]].extend(rows)

        for first, rows in groups.items():
            orcids = {a.orcid for _, a in rows if a.orcid} | orcid_by_name.get((last, first), set())
            label = max((_label(a) for _, a in rows), key=len)
            ident = "orcid:" + next(iter(orcids)) if len(orcids) == 1 else f"name:{last}|{first}"
            for pmid, au in rows:
                table.add(EntityType.AUTHOR, ident, label, pmid, _label(au), **_orcid_attr(orcids))

            variants = sorted({_label(a) for _, a in rows})
            if len(variants) > 1:
                initial_merge = any(len(f) == 1 for f, t in target.items() if t == first and f != t)
                table.log.append(
                    LogEntry(
                        type=EntityType.AUTHOR,
                        action="merged",
                        label=label,
                        variants=variants,
                        uncertain=initial_merge,
                        reason=(
                            "Initial matched to a full first name"
                            if initial_merge
                            else "Same surname and first name after case/accent folding"
                        ),
                    )
                )
            if len(orcids) > 1:
                table.log.append(
                    LogEntry(
                        type=EntityType.AUTHOR,
                        action="kept_separate",
                        label=label,
                        variants=sorted(orcids),
                        uncertain=True,
                        reason="Same name but different ORCIDs; grouped by name",
                    )
                )


def _label(au: Author) -> str:
    return au.display_name


def _orcid_attr(orcids: set[str]) -> dict[str, str]:
    return {"orcid": next(iter(orcids))} if len(orcids) == 1 else {}


# --- Institutions ------------------------------------------------------------------

# Ported from ProcFC.parseInst (legacy/src/bales/ProcFC.java), extended.
_INSTITUTION_WORDS = [
    "academy",
    "agency",
    "centers",
    "center",
    "centre",
    "clinic",
    "clinique",
    "college",
    "corporation",
    "council",
    "foundation",
    "hopital",
    "hospital",
    "hospitals",
    "inc",
    "institut",
    "institute",
    "institutes",
    "institutet",
    "instituto",
    "istituto",
    "klinikum",
    "laboratoire",
    "laboratory",
    "laboratories",
    "library",
    "ltd",
    "school",
    "univ",
    "universidad",
    "universidade",
    "universita",
    "universitas",
    "universitat",
    "universitatsklinikum",
    "universite",
    "universiteit",
    "university",
    "universitario",
]
_INSTITUTION_RE = re.compile(r"\b(" + "|".join(_INSTITUTION_WORDS) + r")\b")
# Prefer a university or similar over a department, center or school within it.
_PRIMARY_RE = re.compile(r"\b(univ\w*|institut\w*|istituto|hospitals?|hopital|college)\b")
_EMAIL_RE = re.compile(r"\S+@\S+|electronic address:?", re.I)


def short_institution(affiliation: str) -> tuple[str | None, bool]:
    """Pick the comma-separated part that names the institution.

    Returns (name, recognized). Like the 2008 parser, it scans the parts and keeps the
    last one containing an institution word, preferring universities and institutes.
    """
    text = _EMAIL_RE.sub("", affiliation).strip(" .;")
    parts = [p.strip(" .;") for p in re.split(r"[,;]", text) if p.strip(" .;")]
    primary = [p for p in parts if _PRIMARY_RE.search(fold(p))]
    any_match = [p for p in parts if _INSTITUTION_RE.search(fold(p))]
    if primary:
        return primary[-1], True
    if any_match:
        return any_match[-1], True
    return (parts[0] if parts else None), False


def add_institutions(papers: Iterable[Paper], table: EntityTable) -> None:
    variants: dict[str, set[str]] = defaultdict(set)
    unparsed: dict[str, int] = defaultdict(int)
    for paper in papers:
        for au in paper.authors:
            for aff in au.affiliations:
                name, ok = short_institution(aff)
                if not name:
                    continue
                ident = fold(name)
                table.add(EntityType.INSTITUTION, ident, name, paper.pmid, name)
                variants[ident].add(name)
                if not ok:
                    unparsed[name] += 1
    for ident, names in variants.items():
        if len(names) > 1:
            table.log.append(
                LogEntry(
                    type=EntityType.INSTITUTION,
                    action="merged",
                    label=table.entities[f"institution:{ident}"].label,
                    variants=sorted(names),
                    uncertain=False,
                    reason="Same name after case/accent/punctuation folding",
                )
            )
    for name, n in sorted(unparsed.items(), key=lambda kv: -kv[1]):
        table.log.append(
            LogEntry(
                type=EntityType.INSTITUTION,
                action="unparsed",
                label=name,
                variants=[name],
                uncertain=True,
                reason=f"No institution word found; used first part of affiliation ({n}×)",
            )
        )


# --- Grants --------------------------------------------------------------------------

# NIH project numbers: optional application type, activity code (R01, K01, F32, P30 …),
# institute code (MH, NS …) and six-digit serial, e.g. '5 R01 MH067947-02'.
_NIH = re.compile(
    r"(?:^|[^A-Z0-9])\d?\s*([A-Z][A-Z0-9]{2})?\s*-?\s*([A-Z]{2})\s*-?\s*(\d{6})(?!\d)"
)


def grant_key(grant_id: str) -> tuple[str, bool]:
    """Returns (key, has_activity_code).

    'R01 MH067947', 'R01-MH067947' and '5R01MH067947-02' all become 'R01MH067947'.
    A common typo, the letter O for zero in the activity code ('KO1'), is corrected.
    A bare 'MH067947' becomes 'MH067947' and has_activity_code is False.
    """
    up = grant_id.upper().strip()
    if m := _NIH.search(up):
        activity = m.group(1)
        if activity:
            activity = activity[0] + activity[1:].replace("O", "0")
        return (activity or "") + m.group(2) + m.group(3), activity is not None
    return re.sub(r"[^A-Z0-9]", "", up), True


def add_grants(papers: Iterable[Paper], table: EntityTable) -> None:
    """Grants are keyed by NIH project number when recognizable.

    Different activity codes on the same serial (an F32 and a later K01) are different
    awards and stay separate. A serial with no activity code is merged into the one
    award that has that serial, flagged uncertain; if several do, it stays separate.
    """
    rows = []
    for paper in papers:
        for grant in paper.grants:
            # Some records pack several IDs into one field: "P50CA165962, R01CA258384".
            for part in re.split(r"[,;]", grant.grant_id or ""):
                if not part.strip():
                    continue
                g = grant.model_copy(update={"grant_id": part.strip()})
                key, full = grant_key(g.grant_id)
                if key:
                    rows.append((paper.pmid, g, key, full))

    full_by_serial: dict[str, set[str]] = defaultdict(set)
    for _, _, key, full in rows:
        if full and len(key) > 8:
            full_by_serial[key[-8:]].add(key)

    resolved: dict[str, str] = {}
    for _, g, key, full in rows:
        if full or key in resolved:
            continue
        matches = full_by_serial.get(key, set())
        resolved[key] = next(iter(matches)) if len(matches) == 1 else key
        if len(matches) > 1:
            table.log.append(
                LogEntry(
                    type=EntityType.GRANT,
                    action="kept_separate",
                    label=g.grant_id.strip(),
                    variants=sorted(matches),
                    uncertain=True,
                    reason="Serial without activity code matches several awards",
                )
            )

    variants: dict[str, set[str]] = defaultdict(set)
    inferred: set[str] = set()
    for pmid, g, key, full in rows:
        ident = key if full else resolved[key]
        if not full and ident != key:
            inferred.add(ident)
        raw = g.grant_id.strip()
        label = raw + (f" ({g.agency})" if g.agency else "")
        table.add(
            EntityType.GRANT, ident, label, pmid, raw, **({"agency": g.agency} if g.agency else {})
        )
        variants[ident].add(raw)

    for ident, ids in variants.items():
        if len(ids) > 1:
            uncertain = ident in inferred
            table.log.append(
                LogEntry(
                    type=EntityType.GRANT,
                    action="merged",
                    label=table.entities[f"grant:{ident}"].label,
                    variants=sorted(ids),
                    uncertain=uncertain,
                    reason=(
                        "Serial without activity code matched to the only award with it"
                        if uncertain
                        else "Same NIH project number written differently"
                    ),
                )
            )


# --- Controlled vocabularies and simple fields ---------------------------------------


# MeSH check tags: demographic and organism terms indexers add to most records. They
# connect nearly everything, so by default they are left off the map.
CHECK_TAGS = {
    "Humans",
    "Animals",
    "Male",
    "Female",
    "Pregnancy",
    "Infant, Newborn",
    "Infant",
    "Child, Preschool",
    "Child",
    "Adolescent",
    "Young Adult",
    "Adult",
    "Middle Aged",
    "Aged",
    "Aged, 80 and over",
    "Mice",
    "Rats",
    "Cattle",
    "Dogs",
    "Cats",
    "Swine",
    "Rabbits",
    "Guinea Pigs",
    "Cricetinae",
    "Chick Embryo",
    "Horses",
    "Sheep",
    "Chickens",
}


def add_mesh(
    papers: Iterable[Paper],
    table: EntityTable,
    major_only: bool = False,
    exclude_check_tags: bool = True,
) -> None:
    skipped: dict[str, int] = defaultdict(int)
    for paper in papers:
        for m in paper.mesh:
            if major_only and not m.major:
                continue
            if exclude_check_tags and m.name in CHECK_TAGS:
                skipped[m.name] += 1
                continue
            table.add(EntityType.MESH, m.ui, m.name, paper.pmid, ui=m.ui)
    if skipped:
        table.log.append(
            LogEntry(
                type=EntityType.MESH,
                action="excluded",
                label="MeSH check tags",
                variants=[f"{k} ({v})" for k, v in sorted(skipped.items(), key=lambda kv: -kv[1])],
                uncertain=False,
                reason="Demographic/organism check tags link almost every paper; left off the map",
            )
        )


def add_substances(
    papers: Iterable[Paper], table: EntityTable, skip_mesh_duplicates: bool = False
) -> None:
    """Substances from the chemical list.

    Many chemicals are also indexed as MeSH headings with the same UI. When MeSH terms
    are on the map too, those duplicates are skipped so one concept isn't drawn twice.
    """
    skipped: dict[str, int] = defaultdict(int)
    for paper in papers:
        mesh_uis = {m.ui for m in paper.mesh} if skip_mesh_duplicates else set()
        for s in paper.substances:
            if s.ui and s.ui in mesh_uis:
                skipped[s.name] += 1
                continue
            table.add(EntityType.SUBSTANCE, s.ui or fold(s.name), s.name, paper.pmid)
    if skipped:
        table.log.append(
            LogEntry(
                type=EntityType.SUBSTANCE,
                action="excluded",
                label="Substances also indexed as MeSH terms",
                variants=[f"{k} ({v})" for k, v in sorted(skipped.items(), key=lambda kv: -kv[1])],
                uncertain=False,
                reason="Same MeSH UI on the same paper; shown once, as the MeSH term",
            )
        )


def add_journals(papers: Iterable[Paper], table: EntityTable) -> None:
    for paper in papers:
        if paper.journal:
            table.add(EntityType.JOURNAL, fold(paper.journal), paper.journal, paper.pmid)


def add_keywords(papers: Iterable[Paper], table: EntityTable) -> None:
    variants: dict[str, set[str]] = defaultdict(set)
    for paper in papers:
        for k in paper.keywords:
            ident = fold(k)
            if ident:
                table.add(EntityType.KEYWORD, ident, k, paper.pmid, k)
                variants[ident].add(k)
    for ident, names in variants.items():
        if len(names) > 1:
            table.log.append(
                LogEntry(
                    type=EntityType.KEYWORD,
                    action="merged",
                    label=table.entities[f"keyword:{ident}"].label,
                    variants=sorted(names),
                    uncertain=False,
                    reason="Same keyword after case/accent/punctuation folding",
                )
            )


def build_entities(
    papers: list[Paper],
    types: Iterable[EntityType],
    mesh_major_only: bool = False,
    exclude_check_tags: bool = True,
) -> EntityTable:
    table = EntityTable()
    types = list(types)
    for t in types:
        match t:
            case EntityType.AUTHOR:
                add_authors(papers, table)
            case EntityType.MESH:
                add_mesh(
                    papers,
                    table,
                    major_only=mesh_major_only,
                    exclude_check_tags=exclude_check_tags,
                )
            case EntityType.JOURNAL:
                add_journals(papers, table)
            case EntityType.INSTITUTION:
                add_institutions(papers, table)
            case EntityType.GRANT:
                add_grants(papers, table)
            case EntityType.SUBSTANCE:
                add_substances(papers, table, skip_mesh_duplicates=EntityType.MESH in types)
            case EntityType.KEYWORD:
                add_keywords(papers, table)
    return table
