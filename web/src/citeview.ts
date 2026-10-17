// "Papers by citation": the citation map as a graph for the map renderer, plus its
// side-panel views. Mark area shows impact (iCite's Relative Citation Ratio), so the
// most influential papers stand out and get labels first.

import { iconHtml, TYPE_STYLES } from "./encoding";
import { esc } from "./map";
import type { Graph, GraphNode, Run } from "./types";

const fmt = (n: number) => n.toLocaleString();
// Papers without an RCR (too recent, or not covered) are drawn at this size.
const NO_RCR_SIZE = 0.15;

export function buildCitationGraph(run: Run): Graph | null {
  const cm = run.citations;
  if (!cm) return null;
  const nodes: GraphNode[] = cm.papers.map((p) => {
    const info = run.papers[p.pmid];
    const title = info?.title || `PMID ${p.pmid}`;
    return {
      id: p.pmid,
      type: "paper",
      label: title,
      count: 1,
      size: Math.max(p.rcr ?? NO_RCR_SIZE, NO_RCR_SIZE),
      pmids: [p.pmid],
      variants: [],
      cluster: p.cluster,
      x: p.x,
      y: p.y,
      stability: p.stability,
      search: [title, info?.journal, info?.year, ...(info?.authors ?? [])].filter(Boolean).join(" ").toLowerCase(),
    };
  });
  return {
    run_id: run.graph.run_id,
    query: run.graph.query,
    nodes,
    edges: cm.edges.map((e) => ({ source: e.source, target: e.target, weight: e.weight, count: e.shared })),
    clusters: cm.clusters.map((c) => ({
      id: c.id,
      size: c.size,
      label: c.label,
      top_members: c.representative.map((pmid) => ({ key: pmid, label: run.papers[pmid]?.title ?? pmid, type: "paper" as const, count: 1 })),
    })),
    low_stability: cm.low_stability,
    regions: cm.layout_method === "spectral_tsne" ? "islands" : "clusters",
  };
}

function step(run: Run): Record<string, any> {
  return run.manifest.steps.find((s) => s.name === "Citation map")?.counts ?? {};
}

export function citationOverviewHtml(run: Run): string {
  const cm = run.citations!;
  const c = step(run);
  const direct = cm.edges.filter((e) => e.direct).length;
  const tsne = cm.layout_method === "spectral_tsne";
  return `
    <h2 class="panel-title">${esc(run.manifest.query)}</h2>
    <p class="small muted">Papers by citation: papers are linked when one cites the other or they share references. Mark size shows each paper's Relative Citation Ratio.</p>
    <dl class="stats">
      <div><dt>Papers linked</dt><dd>${fmt(cm.papers.length)}</dd></div>
      <div><dt>Citation clusters</dt><dd>${fmt(cm.clusters.length)}</dd></div>
      <div><dt>Direct citations</dt><dd>${fmt(direct)}</dd></div>
    </dl>
    <p class="small muted">${fmt(c.found_in_icite ?? 0)} of ${fmt(c.papers ?? 0)} papers found in NIH iCite; ${fmt(c.unlinked ?? 0)} had no citation links to the rest of the set and aren't drawn.</p>
    <h3>Citation clusters</h3>
    <p class="muted small">Groups of papers that cite each other or draw on the same references, which often marks a research community. Named by distinctive terms in their titles and abstracts.</p>
    <ol class="cluster-list">
      ${cm.clusters
        .map(
          (cl) => `<li><button type="button" class="row-button" data-cluster="${cl.id}">
            <span>${esc(cl.label)}</span><span class="muted num">${cl.size}</span></button></li>`,
        )
        .join("")}
    </ol>
    <h3>Reading the map</h3>
    <ul class="reading small">
      <li><strong>Size</strong> is the Relative Citation Ratio (RCR): citations per year compared with papers in the same field and year. 1.0 is the median for NIH-funded papers; 2.0 means cited twice as much. Papers without an RCR are drawn smallest.</li>
      <li>${tsne
        ? "Papers with similar citation links sit near each other. Only <strong>local</strong> neighborhoods are reliable."
        : "Linked papers are pulled together; distances are approximate."}
        This layout was chosen because it matched the clusters better than the alternative (see How this map was made).</li>
      <li>Select a paper to see what it cites, what cites it, and which papers share the most references with it.</li>
    </ul>`;
}

function percentile(p: number | null): string {
  return p == null ? "" : `; NIH percentile ${p.toFixed(1)}`;
}

export function citationPaperDetailHtml(run: Run, pmid: string, paperItem: (pmid: string) => string): string {
  const p = run.papers[pmid];
  const cp = run.citations!.papers.find((x) => x.pmid === pmid);
  const cluster = cp && cp.cluster >= 0 ? run.citations!.clusters[cp.cluster] : null;
  const entities = run.graph.nodes.filter((n) => n.pmids.includes(pmid)).sort((a, b) => b.count - a.count);
  const list = (ids: string[]) => `<ol class="paper-list">${ids.map(paperItem).join("")}</ol>`;
  return `
    <button type="button" class="link back">← Overview</button>
    <div class="node-head">${iconHtml("paper", 28)}<span class="muted">${esc([p?.year, p?.journal].filter(Boolean).join(" · "))}</span></div>
    <h2 class="panel-title">${esc(p?.title || `PMID ${pmid}`)}</h2>
    ${p?.authors.length ? `<p class="small">${esc(p.authors.join(", "))}</p>` : ""}
    <p class="small"><a href="${esc(p?.url ?? `https://pubmed.ncbi.nlm.nih.gov/${pmid}/`)}" target="_blank" rel="noopener">PubMed ${esc(pmid)}</a>
      · <a href="https://pubmed.ncbi.nlm.nih.gov/?linkname=pubmed_pubmed_citedin&from_uid=${esc(pmid)}" target="_blank" rel="noopener">Cited by (PubMed)</a></p>
    ${cp ? `<dl class="stats">
      <div><dt>Relative Citation Ratio</dt><dd>${cp.rcr == null ? "–" : cp.rcr.toFixed(2)}</dd></div>
      <div><dt>Times cited</dt><dd>${fmt(cp.citation_count)}</dd></div>
      <div><dt>Layout stability</dt><dd>${cp.stability.toFixed(2)}</dd></div>
    </dl>
    <p class="small muted">${cp.rcr == null ? "No RCR: the paper is too recent or not covered by iCite." : `RCR 1.0 is the median for NIH-funded papers in the same field and year${percentile(cp.nih_percentile)}.`}${cp.provisional ? " Provisional: the paper is recent, so its RCR may change." : ""}</p>` : ""}
    ${cp && cp.stability < run.citations!.low_stability ? `<div class="banner note">This paper's map neighbors change between layout runs. Rely on the lists below, not its location.</div>` : ""}
    ${cluster ? `<p class="small">Citation cluster: <button type="button" class="link" data-goto-text-cluster="${cluster.id}">${esc(cluster.label)}</button></p>` : cp ? `<p class="small muted">Not in a citation cluster.</p>` : ""}
    ${cp?.cites.length ? `<h3>Cites (in this set)</h3>${list(cp.cites)}` : ""}
    ${cp?.cited_by.length ? `<h3>Cited by (in this set)</h3>${list(cp.cited_by)}` : ""}
    ${cp?.coupled.length ? `<h3>Shares the most references with</h3>
      <p class="muted small">Bibliographic coupling: shared references, and Salton's cosine (shared ÷ √(references × references)).</p>
      <ol class="paper-list">${cp.coupled
        .map((c) => paperItem(c.pmid).replace("</li>", `<span class="sim num" title="Shared references; cosine ${c.cosine.toFixed(2)}">${c.shared} shared</span></li>`))
        .join("")}</ol>` : ""}
    ${cp ? `<p class="small muted">${fmt(cp.references_total)} references in PubMed; ${cp.cites.length} of them are in this set.</p>` : ""}
    ${entities.length ? `<h3>On the entity map</h3>
    <ul class="entity-list">${entities
      .slice(0, 20)
      .map(
        (n) => `<li><button type="button" class="row-button" data-entity="${esc(n.id)}">${iconHtml(n.type, 18)}<span class="grow">${esc(n.label)}</span><span class="muted small">${TYPE_STYLES[n.type].label}</span></button></li>`,
      )
      .join("")}</ul>` : ""}`;
}
