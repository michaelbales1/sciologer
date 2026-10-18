# Sciologer

Sciologer maps the relationships in a set of PubMed search results. Search PubMed, and it shows how the MeSH terms, authors, journals, substances, institutions, grants and keywords in the results connect. Entities that appear on the same papers are linked and drawn near each other.

Michael Bales created Sciologer in 2008 as a Java/Tomcat app. It wrote its sociograms as KML so that Google Earth, with its native rendering of 3D vector graphics, could display them as 3D networks you could fly through with a 3D mouse. The KML held each node's XYZ position in the network layout, not a geographic location. Version 0.2 rebuilds it as a local Python app with an interactive web map. The original code is kept in [`legacy/`](legacy/).

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
| Network | Links entities that share papers, keeping the top entities per type. Authors are counted fractionally by default. Weight = log(1 + association strength) | [`network.py`](sciologer/network.py) |
| Cluster | Leiden communities (modularity with a resolution setting), fixed seed. Each cluster is named by its most distinctive subject terms | [`cluster.py`](sciologer/cluster.py) |
| Layout | ForceAtlas2 with LinLog attraction (the successor to CCVisu's LinLog), run with 5 seeds to score stability | [`layout.py`](sciologer/layout.py) |
| Text map | Embeds each title + abstract, clusters papers in the full embedding space, names clusters by distinctive terms, projects with t-SNE (see below) | [`semantic.py`](sciologer/semantic.py) |
| Citation map | Links papers by direct citation and shared references using NIH iCite, clusters them, sizes them by Relative Citation Ratio (see below) | [`citations.py`](sciologer/citations.py) |
| Geography | Matches each affiliation to an institution in the Research Organization Registry (ROR), places it at its city, links institutions that share papers (see below) | [`geo.py`](sciologer/geo.py) |

Every run writes a **manifest** with the query, PubMed's interpretation of it, how many records matched and how many were fetched, all parameters, software versions, seeds and the full name-matching log. The app shows it under **How this map was made**. You can download it with the map as JSON, GEXF (for Gephi), GraphML (for Cytoscape) or CSV.

### Clustering

Both views use the Leiden algorithm (Traag, Waltman & van Eck 2019), the method behind VOSviewer and the CWTS publication-level classifications, rather than Louvain. Leiden guarantees that every cluster is internally connected (Louvain can return clusters that fall apart inside), and it keeps refining until no node can move to improve the result. It optimizes the same objective as before, modularity with a resolution parameter, so **Options → Cluster resolution** works as before: higher gives more, smaller clusters.

On the three maps used during development (two topics and one researcher), Leiden and Louvain gave similar results: modularity within ±0.007, and no disconnected clusters with either. The switch is about guarantees, and about supporting nested, multilevel clusters next, rather than a visible change on typical maps.

### Counting authors

By default, authors are counted **fractionally** (Perianes-Rodriguez, Waltman & van Eck 2016). On a paper with A authors, each author earns 1/A credit for it, and each author–author link counts 1/(A − 1) instead of 1. The authors shown are those with the most credit, among those on at least the minimum number of papers.

Without this, a few large consortium papers (80+ authors) can fill the author slots and bind their authors into one block that dominates the map. On a test map of one researcher's 92 papers, full counting gave 55 of the 60 author slots to consortium members. Fractional counting gave 24, and the researcher's regular collaborators moved to the top. Ranking matters as much as links: association strength largely cancels fractional links on their own. To count every author in full, use **Options → Count authors → In full**. The author's credit is shown in the details panel.

### Four views

- **Entities** links MeSH terms, authors, journals and so on when they share papers.
- **Papers by text** places each paper by what its title and abstract say.
- **Papers by citation** links papers that cite each other or share references.
- **Geography** places institutions from author affiliations on a world map, linked when they share papers. It is made on request (see below), because its lookups take minutes.

They answer different questions, so they are separate, linked views rather than one blended map. Co-occurrence shows how indexed concepts and people connect. Text shows what papers are about, including themes that cut across the indexing. Citations show intellectual communities: who builds on whom.

How the text map is made:
- **Embedding.** [NeuML/pubmedbert-base-embeddings-8M](https://huggingface.co/NeuML/pubmedbert-base-embeddings-8M), a Model2Vec distillation of PubMedBERT Embeddings. It runs on the CPU and is downloaded once (about 31 MB) to the Hugging Face cache. Papers without an abstract are placed by title and marked as such.
- **Clusters** are found in the full embedding space, not on the 2D picture, using Leiden on a 10-nearest-neighbor similarity graph. Groups smaller than 5 papers are left unclustered rather than shown as clusters. The silhouette is reported, and a warning appears when clusters overlap heavily.
- **Names** come from class-based TF-IDF. Terms must be shared by several papers, terms common to every cluster are penalized, and the query's own words are left out because every paper matches them. The weighted terms and the most central papers are shown as evidence.
- **Projection.** t-SNE: one run starts from PCA and two from random positions. Only local neighborhoods are meaningful. Each paper gets a stability score (do its map neighbors stay the same?) and a count of how many of its 10 nearest papers by text are near it on the map. Selecting a paper lists its true nearest papers by text.
- t-SNE can split a cluster into several islands. Each island gets its own region and label, so a split theme is visible rather than hidden.

How the citation map is made:
- **Data** come from [NIH iCite](https://icite.od.nih.gov/): each paper's references, citing papers, citation count and **Relative Citation Ratio** (RCR). RCR is citations per year relative to papers in the same field and year; 1.0 is the median for NIH-funded papers. Requests go in batches of 200 PMIDs through a shared rate limiter, and responses are cached.
- **Links:** two papers in the set are linked when one cites the other (weight 1) and/or they share references (bibliographic coupling, Salton's cosine). Coupling also connects recent papers that haven't been cited yet. In a focused topic nearly every pair shares some reference, so only each paper's 5 strongest coupling links are kept, plus every direct citation.
- **Clusters:** Leiden, with groups under 3 papers left unclustered. Names come from distinctive terms in the papers' titles and abstracts.
- **Layout:** two candidates are computed, ForceAtlas2 and a spectral embedding laid out with t-SNE, and the one that agrees better with the clusters is kept. Each won on one kind of network during development: ForceAtlas2 on a researcher's 82 linked papers (agreement 0.90 vs 0.40), t-SNE on 379 papers about one topic (0.70 vs 0.40). Both scores are recorded.
- **Size** shows RCR. Papers without one (too recent, or not covered) are drawn smallest. Selecting a paper lists what it cites and what cites it within the set, and the papers it shares the most references with.
- If iCite can't be reached, the other views are still built and the run notes why the citation view is missing.

How the geographic view is made:
- **On request.** Matching affiliations takes a few minutes for a new map, so the view is off by default. Open the **Geography** tab on any map: it shows how many affiliations need looking up, how many are already cached and roughly how long the rest will take, with a button to start. Only this step runs, in the background, while the other views stay usable. To make it with the map instead, tick **Options → Include the geographic view**. Each map saves its authors' affiliations, so the view can be added later without going back to PubMed. Maps made before that are fetched again by PMID.
- **Matching.** Each distinct affiliation string (without emails and URLs) is sent to the [ROR](https://ror.org) affiliation-matching service. Only results ROR marks as *chosen*, meaning confident, are placed. Everything else is counted and listed under "not placed" rather than guessed. On test data, 84% of distinct affiliations got a confident match. Full strings match better than shortened ones (35 vs 31 of 40 in a sample), so they are sent whole.
- **Institutions** are identified by their ROR ID, so different spellings of one institution become one mark. The details panel lists the affiliation strings that matched it and links to its ROR record.
- **Location** is the city ROR records for the institution, so institutions in the same city share a point. The map spreads them around it, largest in the middle. The projection is Equal Earth, which keeps areas true, and the outlines are Natural Earth's (public domain).
- **Size** is the number of papers with an author at the institution. **Links** join institutions that appear on the same paper; their weight is the number of shared papers. The 300 institutions with the most papers are drawn, and the side panel lists papers by country.
- **Rate limits.** ROR allows 2,000 requests per 5 minutes without registration and has announced a lower limit (50 per 5 minutes) for unregistered clients later. Requests go through a shared rate limiter that honors Retry-After, the most frequent affiliations are looked up first (up to 2,500 per map), and every answer is cached, so overlapping queries reuse earlier lookups. A first map of 400 papers needs a few hundred to about 1,500 lookups, roughly 1–5 minutes. To use a registered ROR client ID, set `ROR_CLIENT_ID`.
- In batch studies the view is made only when `geography` is listed under `views`.

The views link to each other: a paper lists its entities, and any paper list links to that paper's place on the text and citation maps.

### Name matching

- **Authors** are keyed by ORCID when there is one, otherwise by surname plus first name, ignoring case and accents. An initial such as "T Südhof" is merged into the one full name that matches it, and the merge is flagged. If several full names match, the records stay separate and the case is flagged.
- **Institutions** are taken from the comma-separated part of an affiliation that names one. Universities and institutes are preferred over departments. This is extended from the 2008 `parseInst`. Affiliations with no recognizable institution are flagged.
- **Grants** are keyed by NIH project number. `R01 MH067947`, `5R01MH067947-02` and `R01-MH067947` count as one grant. An F32 and a K01 with the same serial number stay separate, since they're different awards.
- **MeSH check tags** (Humans, Male, Mice…) are left out by default because they link nearly every paper. **Substances** that are also indexed as MeSH terms are shown once.

## Batch studies

A **study** runs a list of PubMed queries with identical settings, such as journals over the same years, departments, universities or authors in a field, and compares their collaboration networks. Start one from Terminal and come back later:

```bash
caffeinate -i uv run sciologer batch examples/informatics-journals-2021-2026.yaml
```

`caffeinate -i` keeps a Mac awake while it runs. Then open the app and choose **Studies**, or open `~/.sciologer/studies/<study>/metrics.csv` in a spreadsheet or R.

A study file lists shared settings and the queries. See [`examples/informatics-journals-2021-2026.yaml`](examples/informatics-journals-2021-2026.yaml):

```yaml
name: Informatics journals 2021-2026
settings:
  types: [author, institution, mesh]
  views: [entities, text, citations]
  max_records: all            # every matching paper
  # sample: 2000              # or a seeded random sample for very large sets
queries:
  - {label: JBI,   query: '"J Biomed Inform"[TA] AND 2021:2026[DP]'}
  - {label: JAMIA, query: '"J Am Med Inform Assoc"[TA] AND 2021:2026[DP]'}
```

What a batch does, for each query:
- **Retrieves the complete set**, not PubMed's first N. Sets over 10,000 papers (PubMed's limit per search) are split by publication date and recombined. Sets over 20,000 need either `sample:`, a reproducible random sample, or `allow_large: true`.
- **Freezes the PMIDs** in `pmids/<label>.txt`, so rerunning the study later reproduces the same sets even as PubMed grows. Use `--refresh` to search again.
- **Builds the maps**, saved as ordinary runs that you can open from the Studies page.
- **Computes collaboration measures** on the complete co-authorship network: every author after name matching, not just those drawn on the map. The measures are team size, single-author and 10+ author shares, multi-institution share, density, components and the largest component's share, clustering, transitivity, Leiden modularity, mean path length, degree assortativity and repeat collaboration. Each is defined on the Studies page. Papers with more than 100 authors are left out of the network measures (and counted), as is common in co-authorship studies.
- **Saves progress after every query.** Rerunning the same command resumes; a failed query is recorded, and the rest continue.

When comparing sets, keep in mind that density falls as networks grow, and that path length is measured within each set's largest connected group. The Studies page notes these caveats beside the measures.

Commands: `sciologer batch FILE [--refresh] [--only LABEL]` and `sciologer studies`.

## Visual design

The map follows principles from the information-visualization literature and from the [2026 *Bibliometric Visualization Update*](#background) that motivated this rewrite.

- **One encoding per channel.** Position comes from the layout. Pictogram plus hue gives entity type. Area grows with the number of papers. Link opacity shows link strength. Clusters are drawn as neutral regions, so they don't compete with type for hue.
- **The original Sciologer icons.** Each type is drawn with its 2008 pictogram (person, MeSH triangle, journal, flask, building, dollar sign, plus a tag drawn to match for keywords), tinted with the type's color. A mark too small for its pictogram to read becomes a simple shape that echoes it (person → circle, building → house) and turns into the pictogram as you zoom in. `scripts/make_icons.py` converts `legacy/drawing/` into the line and fill masks in `web/src/assets/icons/`.
- **Colorblind-safe types.** Seven hues can't all be told apart in a scatter-like map; the palette validator puts them below the colorblind and normal-vision floors. So the pictogram is the primary type signal, and the three default types (MeSH, authors, journals) use the three hues that stay distinct for every pair, in light and dark themes.
- **Overview first, then zoom and filter, then details on demand.** Weak links are hidden until you select a node. Labels are placed only where they fit. Selecting a node shows its papers (linked to PubMed), the entities it co-occurs with most, and its MeSH, ORCID or NIH RePORTER record.
- **Linked views.** Brushing the papers-per-year histogram filters the map, the counts and the entity table. Step buttons move the selected years backward and forward one year at a time.
- **Honesty about the map.** A 2D layout is a navigation aid, not a measurement. A note on the map says that distances are approximate. Nodes whose neighborhood changed across layout runs are drawn hollow with a dashed outline. Entities with no links are listed rather than placed at arbitrary positions.
- **Accessible.** The **Entities** tab is a sortable table version of the map. Keyboard: `/` finds, `Esc` clears the selection. There are light and dark themes.

## Background

The workflow follows the stages described in Bales, Wright, Oxley and Wheeler, *Bibliometric Visualization and Analysis Software: State of the Art, Workflows, and Best Practices*: retrieval, preprocessing, extraction, normalization, mapping, analysis and visualization. Several of its recommendations are implemented directly:
- abstracts treated as short texts and compared with neural document vectors
- a minimum cluster size, so groups of one or two articles aren't shown as clusters
- silhouette values reported for cluster separation
- labels from weighted terms
- search within displayed articles by title, year, author or journal
- a control that moves the map through time
- impact measures integrated into the map (RCR), which the paper notes few tools do
- information shown on demand to limit cognitive load

The design also responds to the recommendations in *Bibliometric Visualization Update* (Undermind, October 2026):

| Recommendation | In Sciologer |
|---|---|
| Keep analytical choices visible [Nowakowska 2025; Atzberger et al. 2024] | Run manifest; "How this map was made" panel; every parameter in the export |
| Document normalization; inspect uncertain matches [Nowakowska 2025] | Name-matching log with flagged items |
| Treat maps as navigation aids, not ground truth [González-Márquez et al. 2024] | Approximate-distance note, stability encoding, details come from the data rather than position |
| Match the representation to the question [Xie & Waltman 2023] | Separate entity (co-occurrence), text (embedding) and citation views, linked but not blended |
| Separate high-dimensional similarity from the 2D projection [González-Márquez et al. 2024] | Text clusters and "nearest by text" come from the embeddings; the map reports how many neighbors it keeps |
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

- Co-citation as an alternative citation link, for older literature
- Cluster labels from a language model, grounded in the cluster's papers and checked against them
- Multilevel clusters with Leiden: zoom from broad areas into specialties
- Geographic view: optionally aggregate institutions into cities or countries when zoomed out
