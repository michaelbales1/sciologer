// "Papers by text": the text map as a graph the map renderer can draw, plus the side
// panel views for it. Papers are marks; similarity links (each paper's nearest papers
// by text) stay hidden until a paper is selected.

import { iconHtml, TYPE_STYLES } from "./encoding";
import { esc } from "./map";
import type { Graph, GraphNode, MapIsland, Run, TextMapCluster } from "./types";

const fmt = (n: number) => n.toLocaleString();

export function buildTextGraph(run: Run): Graph | null {
  const text = run.text;
  if (!text) return null;
  const nodes: GraphNode[] = text.papers.map((p) => {
    const info = run.papers[p.pmid];
    const title = info?.title || `PMID ${p.pmid}`;
    return {
      id: p.pmid,
      type: "paper",
      label: title,
      count: 1,
      pmids: [p.pmid],
      variants: [],
      cluster: p.cluster,
      x: p.x,
      y: p.y,
      stability: p.stability,
      neighbors_kept: p.neighbors_kept,
      island: p.island,
      search: [title, info?.journal, info?.year, ...(info?.authors ?? [])].filter(Boolean).join(" ").toLowerCase(),
    };
  });
  return {
    run_id: run.graph.run_id,
    query: run.graph.query,
    nodes,
    edges: text.edges.map((e) => ({ source: e.source, target: e.target, weight: e.similarity, count: 1 })),
    clusters: text.clusters.map((c) => ({
      id: c.id,
      size: c.size,
      label: c.label,
      top_members: c.representative.map((pmid) => ({
        key: pmid,
        label: run.papers[pmid]?.title ?? pmid,
        type: "paper" as const,
        count: 1,
      })),
    })),
    low_stability: text.low_stability,
    uniform_size: true,
    islands: text.islands,
  };
}

function textStep(run: Run): Record<string, any> {
  return run.manifest.steps.find((s) => s.name === "Text map")?.counts ?? {};
}

export function textOverviewHtml(run: Run, years: [number, number] | null): string {
  const text = run.text!;
  const c = textStep(run);
  const unclustered = text.papers.filter((p) => p.cluster < 0).length;
  const sil = typeof c.silhouette === "number" ? c.silhouette : null;
  return `
    <h2 class="panel-title">${esc(run.manifest.query)}</h2>
    <p class="small muted">Papers by text: each paper is placed by what its title and abstract say.</p>
    <dl class="stats">
      <div><dt>Papers${years ? " (all years)" : ""}</dt><dd>${fmt(text.papers.length)}</dd></div>
      <div><dt>Text clusters</dt><dd>${fmt(text.clusters.length)}</dd></div>
      <div><dt>Nearest kept on map</dt><dd>${c.median_neighbors_kept ?? "–"} of ${c.neighbors_compared ?? 10}</dd></div>
    </dl>
    ${sil !== null && sil < 0.1 ? `<div class="banner warn">The text clusters overlap heavily (silhouette ${sil}). Read them as broad themes, not distinct groups.</div>` : ""}
    <h3>Text clusters</h3>
    <p class="muted small">Named by terms that are frequent in the cluster and rare elsewhere. The query's own words are left out because every paper matches them. Select a cluster to see the evidence.</p>
    <ol class="cluster-list">
      ${text.clusters
        .map(
          (cl) => `<li><button type="button" class="row-button" data-cluster="${cl.id}">
            <span>${esc(cl.label)}</span><span class="muted num">${cl.size}</span></button></li>`,
        )
        .join("")}
    </ol>
    ${unclustered ? `<p class="small muted">${unclustered} paper${unclustered === 1 ? "" : "s"} fell in groups too small to count as clusters.</p>` : ""}
    <h3>Reading the map</h3>
    <ul class="reading small">
      <li>Papers about similar things sit near each other. Only <strong>local</strong> neighborhoods are reliable: the distance between two clusters means little.</li>
      <li>Clusters were found in the full text representation, not from positions on this map, so a cluster can look scattered.</li>
      <li>A 2D map can't keep every neighbor close. For the typical paper, ${c.median_neighbors_kept ?? "–"} of its ${c.neighbors_compared ?? 10} nearest papers by text are among its nearest on the map; select a paper to see its true nearest.</li>
      <li>Hollow, dashed papers have map neighbors that change between layout runs.</li>
    </ul>`;
}

export function textClusterHtml(
  run: Run,
  cl: TextMapCluster,
  paperItem: (pmid: string) => string,
  members: string[] = run.text!.papers.filter((p) => p.cluster === cl.id).map((p) => p.pmid),
  back = "← All text clusters",
  centralNote = "the most central papers",
  islands: MapIsland[] = run.text?.islands ?? [],
): string {
  const max = Math.max(...cl.terms.map(([, w]) => w), 1e-9);
  const parts = islands.filter((i) => i.cluster === cl.id && i.label);
  return `
    <button type="button" class="link back">${back}</button>
    <h2 class="panel-title">${esc(cl.label)}</h2>
    <p class="muted small">${cl.size} papers. The name is the top three terms below, from the papers' titles and abstracts; check them against ${centralNote}.</p>
    ${parts.length
      ? `<div class="banner note small">This cluster is drawn in ${parts.length} places on the map. The papers are grouped together by their text, but the 2D layout separated them, which often means the cluster holds distinct subtopics. Each part is labeled on the map by its own distinctive terms:
          <ul>${parts.map((i) => `<li><strong>${esc(i.label!)}</strong> <span class="muted">(${i.size} papers)</span></li>`).join("")}</ul></div>`
      : ""}
    <h3>Distinctive terms</h3>
    <ul class="term-list small">${cl.terms
      .map(
        ([t, w]) => `<li><span class="term">${esc(t)}</span>
          <span class="term-bar" style="--w:${Math.round((w / max) * 100)}%" aria-hidden="true"></span>
          <span class="muted num">${w.toFixed(1)}</span></li>`,
      )
      .join("")}</ul>
    <h3>Most central papers</h3>
    <ol class="paper-list">${cl.representative.map(paperItem).join("")}</ol>
    <details class="small"><summary>All ${members.length} papers</summary>
      <ol class="paper-list">${members.map(paperItem).join("")}</ol></details>`;
}

export function paperDetailHtml(run: Run, pmid: string, paperItem: (pmid: string) => string): string {
  const p = run.papers[pmid];
  const tp = run.text?.papers.find((x) => x.pmid === pmid);
  const cluster = tp && tp.cluster >= 0 ? run.text!.clusters[tp.cluster] : null;
  const entities = run.graph.nodes.filter((n) => n.pmids.includes(pmid)).sort((a, b) => b.count - a.count);
  const nearest = tp
    ? tp.neighbors.map((id, i) => ({ id, sim: tp.similarity[i] })).filter((x) => run.papers[x.id])
    : [];
  return `
    <button type="button" class="link back">← Overview</button>
    <div class="node-head">${iconHtml("paper", 28)}<span class="muted">${esc([p?.year, p?.journal].filter(Boolean).join(" · "))}</span></div>
    <h2 class="panel-title">${esc(p?.title || `PMID ${pmid}`)}</h2>
    ${p?.authors.length ? `<p class="small">${esc(p.authors.join(", "))}</p>` : ""}
    <p class="small"><a href="${esc(p?.url ?? `https://pubmed.ncbi.nlm.nih.gov/${pmid}/`)}" target="_blank" rel="noopener">PubMed ${esc(pmid)}</a>${p?.doi ? ` · <a href="https://doi.org/${esc(p.doi)}" target="_blank" rel="noopener">DOI</a>` : ""}</p>
    ${tp ? `<dl class="stats">
      <div><dt>Nearest by text, nearby on map</dt><dd>${tp.neighbors_kept} of ${tp.neighbors.length}</dd></div>
      <div><dt>Layout stability</dt><dd>${tp.stability.toFixed(2)}</dd></div>
    </dl>` : ""}
    ${tp?.title_only ? `<div class="banner note">No abstract in PubMed, so this paper was placed by its title alone.</div>` : ""}
    ${tp && tp.stability < run.text!.low_stability ? `<div class="banner note">This paper's map neighbors change between layout runs. Rely on the list below, not its location.</div>` : ""}
    ${cluster ? `<p class="small">Text cluster: <button type="button" class="link" data-goto-text-cluster="${cluster.id}">${esc(cluster.label)}</button></p>` : tp ? `<p class="small muted">Not in a text cluster.</p>` : ""}
    ${nearest.length ? `<h3>Nearest by text</h3>
    <p class="muted small">Measured in the full text representation, not on the map. Cosine similarity, higher is closer.</p>
    <ol class="paper-list">${nearest.map((x) => paperItem(x.id).replace("</li>", `<span class="sim num" title="Cosine similarity">${x.sim.toFixed(2)}</span></li>`)).join("")}</ol>` : ""}
    ${entities.length ? `<h3>On the entity map</h3>
    <ul class="entity-list">${entities
      .slice(0, 20)
      .map(
        (n) => `<li><button type="button" class="row-button" data-entity="${esc(n.id)}">${iconHtml(n.type, 18)}<span class="grow">${esc(n.label)}</span><span class="muted small">${TYPE_STYLES[n.type].label}</span></button></li>`,
      )
      .join("")}</ul>` : ""}`;
}

export function paperTableHtml(
  run: Run,
  rows: string[],
  sort: "year" | "title" | "cluster" | "rcr",
  data: { papers: { pmid: string; cluster: number; rcr?: number | null }[]; clusters: TextMapCluster[] } = run.text!,
): string {
  const text = data;
  const byId = new Map(text.papers.map((p) => [p.pmid, p]));
  const showRcr = text.papers.some((p) => p.rcr !== undefined);
  const head = (key: typeof sort, label: string, cls = "") =>
    `<th scope="col" class="${cls}" aria-sort="${sort === key ? (key === "year" || key === "rcr" ? "descending" : "ascending") : "none"}">
      <button type="button" class="th-button" data-paper-sort="${key}">${label}</button></th>`;
  return `
    <p class="muted small">${fmt(rows.length)} papers. Select a row to find it on the map.</p>
    <table class="entity-table">
      <thead><tr>${head("title", "Title")}${head("year", "Year", "num")}${showRcr ? head("rcr", "RCR", "num") : ""}${head("cluster", "Cluster")}</tr></thead>
      <tbody>${rows
        .map((pmid) => {
          const p = run.papers[pmid];
          const tp = byId.get(pmid)!;
          const cl = tp.cluster >= 0 ? text.clusters[tp.cluster].label : "–";
          return `<tr><td><button type="button" class="link" data-node="${esc(pmid)}">${esc(p?.title || pmid)}</button></td>
            <td class="num">${p?.year ?? ""}</td>${showRcr ? `<td class="num">${tp.rcr == null ? "–" : tp.rcr.toFixed(2)}</td>` : ""}<td class="small muted">${esc(cl)}</td></tr>`;
        })
        .join("")}</tbody>
    </table>`;
}
