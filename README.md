# Sciologer

Sciologer maps the relationships in a set of PubMed search results. Search PubMed, and it shows how the MeSH terms, authors, journals, substances, institutions, grants and keywords in the results connect. Entities that appear on the same papers are linked and drawn near each other.

Michael Bales created Sciologer in 2008 as a Java/Tomcat app that wrote KML files for Google Earth. Version 0.2 rebuilds it as a local Python app with an interactive web map. The original code is kept in [`legacy/`](legacy/).

## Quick start

You need [uv](https://docs.astral.sh/uv/) and [Node.js](https://nodejs.org/). On a Mac with Homebrew, install them with `brew install uv node`.

```bash
uv sync
cd web && npm install && npm run build && cd ..
uv run sciologer serve
```

This opens http://127.0.0.1:8765/ in your browser. The server listens only on this computer.

### From the command line

```bash
uv run sciologer search "neuroligins" -t mesh -t author -t journal --max-records 500
uv run sciologer runs
```

Each run is saved under `~/.sciologer/runs/<run-id>/` and appears in the app's **Saved maps** menu. Set `SCIOLOGER_HOME` to store runs somewhere else.

### PubMed rate limits

NCBI allows 3 requests per second without an API key and 10 with one. Sciologer:
- spaces every request through one limiter shared by all running jobs
- backs off and retries when NCBI answers 429 or 5xx, honoring `Retry-After`
- caches raw responses in `~/.sciologer/cache/`, so rerunning a query makes no new requests

For larger or more frequent searches, [get a free API key](https://www.ncbi.nlm.nih.gov/account/settings/) and identify yourself:

```bash
export NCBI_API_KEY=your-key
export NCBI_EMAIL=you@example.org
```

## How a map is made

| Step | What happens | Code |
|---|---|---|
| Retrieve | E-utilities `esearch` with the history server, then `efetch` in batches of 200, as XML | [`pubmed.py`](sciologer/pubmed.py) |
| Parse | XML → papers with authors, ORCIDs, affiliations, MeSH (major/minor), substances, grants, keywords | [`parse.py`](sciologer/parse.py) |
| Normalize | Matches name variants and logs every merge. Uncertain merges are flagged (see below) | [`normalize.py`](sciologer/normalize.py) |
| Network | Links entities that share papers, keeping the most frequent per type. Weight = log(1 + association strength) | [`network.py`](sciologer/network.py) |
| Cluster | Louvain communities with a fixed seed. Each cluster is named by its most distinctive subject terms | [`cluster.py`](sciologer/cluster.py) |
| Layout | ForceAtlas2 with LinLog attraction (the successor to CCVisu's LinLog), run with 5 seeds to score stability | [`layout.py`](sciologer/layout.py) |

Every run writes a **manifest** with the query, PubMed's interpretation of it, how many records matched and how many were fetched, all parameters, software versions, seeds and the full name-matching log. The app shows it under **How this map was made**. You can download it with the map as JSON, GEXF (for Gephi), GraphML (for Cytoscape) or CSV.

### Name matching

- **Authors** are keyed by ORCID when there is one, otherwise by surname plus first name, ignoring case and accents. An initial such as "T Südhof" is merged into the one full name that matches it, and the merge is flagged. If several full names match, the records stay separate and the case is flagged.
- **Institutions** are taken from the comma-separated part of an affiliation that names one. Universities and institutes are preferred over departments. This is extended from the 2008 `parseInst`. Affiliations with no recognizable institution are flagged.
- **Grants** are keyed by NIH project number. `R01 MH067947`, `5R01MH067947-02` and `R01-MH067947` count as one grant. An F32 and a K01 with the same serial number stay separate, since they're different awards.
- **MeSH check tags** (Humans, Male, Mice…) are left out by default because they link nearly every paper. **Substances** that are also indexed as MeSH terms are shown once.

## Visual design

The map follows principles from the information-visualization literature and from the [2026 *Bibliometric Visualization Update*](#background) that motivated this rewrite.

- **One encoding per channel.** Position comes from the layout. Pictogram plus hue gives entity type. Area grows with the number of papers. Link opacity shows link strength. Clusters are drawn as neutral regions, so they don't compete with type for hue.
- **The original Sciologer icons.** Each type is drawn with its 2008 pictogram (person, MeSH triangle, journal, flask, building, dollar sign, plus a tag drawn to match for keywords), tinted with the type's color. A mark too small for its pictogram to read becomes a simple shape that echoes it (person → circle, building → house) and turns into the pictogram as you zoom in. `scripts/make_icons.py` converts `legacy/drawing/` into the line and fill masks in `web/src/assets/icons/`.
- **Colorblind-safe types.** Seven hues can't all be told apart in a scatter-like map; the palette validator puts them below the colorblind and normal-vision floors. So the pictogram is the primary type signal, and the three default types (MeSH, authors, journals) use the three hues that stay distinct for every pair, in light and dark themes.
- **Overview first, then zoom and filter, then details on demand.** Weak links are hidden until you select a node. Labels are placed only where they fit. Selecting a node shows its papers (linked to PubMed), the entities it co-occurs with most, and its MeSH, ORCID or NIH RePORTER record.
- **Linked views.** Brushing the papers-per-year histogram filters the map, the counts and the entity table.
- **Honesty about the map.** A 2D layout is a navigation aid, not a measurement. A note on the map says that distances are approximate. Nodes whose neighborhood changed across layout runs are drawn hollow with a dashed outline. Entities with no links are listed rather than placed at arbitrary positions.
- **Accessible.** The **Entities** tab is a sortable table version of the map. Keyboard: `/` finds, `Esc` clears the selection. There are light and dark themes.

## Background

The design responds to the recommendations in *Bibliometric Visualization Update* (Undermind, October 2026):

| Recommendation | In Sciologer |
|---|---|
| Keep analytical choices visible [Nowakowska 2025; Atzberger et al. 2024] | Run manifest; "How this map was made" panel; every parameter in the export |
| Document normalization; inspect uncertain matches [Nowakowska 2025] | Name-matching log with flagged items |
| Treat maps as navigation aids, not ground truth [González-Márquez et al. 2024] | Approximate-distance note, stability encoding, details come from the data rather than position |
| Match the representation to the question [Xie & Waltman 2023] | Co-occurrence of indexed entities now. A text-embedding mode and a citation mode are planned as separate views, not blended into one map |
| Ground labels in inspectable evidence [Murray et al. 2025] | Cluster names are built from member terms, and the members are listed beside them |

## Development

```bash
uv run pytest            # tests use recorded PubMed fixtures; no network
uv run ruff check .
cd web && npm run dev    # live-reloading UI; run `uv run sciologer serve --no-open` alongside
cd web && npm run build  # builds into sciologer/static/
```

### Layout

```
sciologer/   Python package: pipeline, API server (FastAPI) and CLI
web/         TypeScript UI (Vite, d3 modules, Canvas 2D)
tests/       pytest suite and PubMed XML fixtures
legacy/      the original 2008 Java implementation, for reference
```

## Roadmap

- Abstract embeddings and a text-similarity map mode, kept separate from the co-occurrence map
- A citation mode using NIH iCite or OpenAlex
- Cluster labels from a language model, grounded in the cluster's papers and checked against them
- Multilevel clusters: zoom from broad areas into specialties
