import "./style.css";
import { api } from "./api";
import { iconHtml, TYPE_ORDER, TYPE_STYLES } from "./encoding";
import { esc, LOW_STABILITY, NetworkMap, truncate } from "./map";
import { Timeline } from "./timeline";
import type { Defaults, EntityType, GraphNode, LogEntry, Run } from "./types";

const $ = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T;
const fmt = (n: number) => n.toLocaleString();

// --- theme ----------------------------------------------------------------------

const THEME_KEY = "sciologer-theme";
function applyTheme(t: string | null) {
  if (t) document.documentElement.dataset.theme = t;
  else delete document.documentElement.dataset.theme;
}
try {
  applyTheme(localStorage.getItem(THEME_KEY));
} catch {
  /* storage unavailable */
}
$("theme").addEventListener("click", () => {
  const dark =
    document.documentElement.dataset.theme === "dark" ||
    (!document.documentElement.dataset.theme && matchMedia("(prefers-color-scheme: dark)").matches);
  const next = dark ? "light" : "dark";
  applyTheme(next);
  try {
    localStorage.setItem(THEME_KEY, next);
  } catch {
    /* ignore */
  }
  map.requestDraw();
  if (run) timeline.setPapers(run.papers);
});

// --- state ----------------------------------------------------------------------

let defaults: Defaults | null = null;
let run: Run | null = null;
let selected: GraphNode | null = null;
let focusCluster: number | null = null;
const shownTypes = new Set<EntityType>(TYPE_ORDER);
let years: [number, number] | null = null;
let showAllPapers = false;
let uncertainOnly = true;
let listSort: "count" | "label" | "type" = "count";

const map = new NetworkMap($("map"), {
  onSelect: (n) => {
    selected = n;
    showAllPapers = false;
    if (n) focusCluster = null;
    showTab("details");
    renderDetails();
  },
  onCluster: (id) => {
    focusCluster = id;
    showTab("details");
    renderDetails();
  },
  onVisibleChange: () => {
    if ($("tab-list").getAttribute("aria-selected") === "true") renderList();
  },
});

const timeline = new Timeline($("timeline"), (r) => {
  years = r;
  map.setFilter({ years: r });
  $("year-range").textContent = r ? (r[0] === r[1] ? `${r[0]} only` : `${r[0]}–${r[1]}`) : "Drag across years to filter the map";
  $("clear-years").hidden = !r;
  renderDetails();
  renderList();
});
$("clear-years").addEventListener("click", () => timeline.clear());

// --- tabs -----------------------------------------------------------------------

type Tab = "details" | "list" | "method";
function showTab(tab: Tab) {
  for (const t of ["details", "list", "method"] as Tab[]) {
    $(`tab-${t}`).setAttribute("aria-selected", String(t === tab));
    $(`panel-${t}`).hidden = t !== tab;
  }
  if (tab === "list") renderList();
}
for (const t of ["details", "list", "method"] as Tab[]) $(`tab-${t}`).addEventListener("click", () => showTab(t));

// --- search form ----------------------------------------------------------------

function renderTypeOptions() {
  const chosen = new Set(defaults?.default_types ?? ["mesh", "author", "journal"]);
  $("type-options").innerHTML = TYPE_ORDER.map(
    (t) => `<label class="check"><input type="checkbox" name="type" value="${t}" ${chosen.has(t) ? "checked" : ""}/>
      ${iconHtml(t, 18)} ${TYPE_STYLES[t].plural}</label>`,
  ).join("");
}

async function startRun(query: string) {
  if (!defaults) return;
  const types = [...document.querySelectorAll<HTMLInputElement>('input[name="type"]:checked')].map((i) => i.value);
  const params = structuredClone(defaults.params);
  params.query = query;
  params.types = types;
  params.max_records = Number(($("max-records") as HTMLInputElement).value) || 500;
  params.mesh_major_only = ($("mesh-major") as HTMLInputElement).checked;
  params.exclude_check_tags = ($("check-tags") as HTMLInputElement).checked;
  params.network.min_occurrences = Number(($("min-occ") as HTMLInputElement).value) || 1;
  params.network.max_per_type = Number(($("max-per-type") as HTMLInputElement).value) || 60;
  params.network.weighting = ($("weighting") as HTMLSelectElement).value;
  params.cluster.resolution = Number(($("resolution") as HTMLInputElement).value) || 1;

  hideError();
  setProgress("Starting…", 0);
  const button = document.querySelector<HTMLButtonElement>("#search button[type=submit]")!;
  button.disabled = true;
  try {
    const job = await api.start(params);
    for (;;) {
      await new Promise((r) => setTimeout(r, 600));
      const s = await api.status(job.run_id);
      setProgress(s.message, s.progress);
      if (s.state === "done") {
        await loadRun(job.run_id);
        break;
      }
      if (s.state === "error") throw new Error(s.error ?? "The run failed");
    }
  } catch (err) {
    showError(err instanceof Error ? err.message : String(err));
  } finally {
    $("progress").hidden = true;
    button.disabled = false;
  }
}

$("search").addEventListener("submit", (e) => {
  e.preventDefault();
  const q = ($("query") as HTMLInputElement).value.trim();
  if (q) startRun(q);
});
document.querySelectorAll<HTMLButtonElement>("[data-example]").forEach((b) =>
  b.addEventListener("click", () => {
    ($("query") as HTMLInputElement).value = b.dataset.example!;
    startRun(b.dataset.example!);
  }),
);

function setProgress(msg: string, frac: number) {
  $("progress").hidden = false;
  $("progress-text").textContent = msg;
  $("progress-bar").style.width = `${Math.round(frac * 100)}%`;
}
function showError(msg: string) {
  $("error").hidden = false;
  $("error").textContent = msg;
}
function hideError() {
  $("error").hidden = true;
}

// --- saved runs -----------------------------------------------------------------

async function refreshRuns(current?: string) {
  const runs = await api.runs();
  const sel = $("runs") as HTMLSelectElement;
  sel.innerHTML =
    `<option value="">Saved maps (${runs.length})</option>` +
    runs
      .map((r) => {
        const when = new Date(r.created_at).toLocaleDateString(undefined, { month: "short", day: "numeric" });
        return `<option value="${esc(r.run_id)}">${esc(truncate(r.query, 40))} · ${fmt(r.papers)} papers · ${when}</option>`;
      })
      .join("");
  if (current) sel.value = current;
}
$("runs").addEventListener("change", (e) => {
  const id = (e.target as HTMLSelectElement).value;
  if (id) loadRun(id).catch((err) => showError(String(err)));
});

async function loadRun(id: string) {
  run = await api.run(id);
  selected = null;
  focusCluster = null;
  years = null;
  ($("query") as HTMLInputElement).value = run.manifest.query;
  $("empty").hidden = true;
  $("map-note").hidden = false;
  const present = new Set(run.graph.nodes.map((n) => n.type));
  for (const t of TYPE_ORDER) if (present.has(t)) shownTypes.add(t);
  map.setData(run.graph, run.papers);
  map.setFilter({ types: shownTypes, years: null });
  timeline.setPapers(run.papers);
  $("year-range").textContent = "Drag across years to filter the map";
  $("clear-years").hidden = true;
  renderLegend();
  renderDetails();
  renderMethod();
  renderList();
  history.replaceState(null, "", `#${id}`);
  await refreshRuns(id);
}

// --- legend (also the type filter) ----------------------------------------------

function renderLegend() {
  if (!run) return;
  const counts = new Map<EntityType, number>();
  for (const n of run.graph.nodes) counts.set(n.type, (counts.get(n.type) ?? 0) + 1);
  const types = TYPE_ORDER.filter((t) => counts.has(t));
  $("legend").innerHTML = types
    .map(
      (t) => `<button type="button" class="legend-item" aria-pressed="${shownTypes.has(t)}" data-type="${t}"
        title="Show or hide ${TYPE_STYLES[t].plural.toLowerCase()}">
        ${iconHtml(t, 18)}<span>${TYPE_STYLES[t].plural}</span><span class="muted num">${counts.get(t)}</span></button>`,
    )
    .join("");
  $("legend").querySelectorAll<HTMLButtonElement>(".legend-item").forEach((b) =>
    b.addEventListener("click", () => {
      const t = b.dataset.type as EntityType;
      if (shownTypes.has(t)) shownTypes.delete(t);
      else shownTypes.add(t);
      b.setAttribute("aria-pressed", String(shownTypes.has(t)));
      map.setFilter({ types: new Set(shownTypes) });
      renderList();
    }),
  );
}

$("link-share").addEventListener("input", (e) => {
  const v = Number((e.target as HTMLInputElement).value);
  $("link-share-value").textContent = v >= 1 ? "all" : `strongest ${Math.round(v * 100)}%`;
  map.setFilter({ linkShare: v });
});
$("find").addEventListener("input", (e) => map.setFilter({ search: (e.target as HTMLInputElement).value }));
$("find").addEventListener("keydown", (e) => {
  if (e.key !== "Enter" || !run) return;
  const q = (e.target as HTMLInputElement).value.trim().toLowerCase();
  const hit = run.graph.nodes
    .filter((n) => shownTypes.has(n.type) && n.label.toLowerCase().includes(q))
    .sort((a, b) => b.count - a.count)[0];
  if (hit) {
    map.select(hit.id);
    map.zoomToNode(hit.id);
  }
});
$("reset-view").addEventListener("click", () => map.resetView());
document.addEventListener("keydown", (e) => {
  if (e.key === "/" && !(e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement)) {
    e.preventDefault();
    $("find").focus();
  }
});

// --- details panel --------------------------------------------------------------

function paperItem(pmid: string): string {
  const p = run!.papers[pmid];
  if (!p) return "";
  const meta = [p.year, p.journal].filter(Boolean).join(" · ");
  return `<li class="paper">${iconHtml("paper", 16)}<div><a href="${esc(p.url)}" target="_blank" rel="noopener">${esc(p.title || `PMID ${pmid}`)}</a>
    <div class="muted small">${esc(meta)}${p.authors.length ? ` — ${esc(p.authors.slice(0, 3).join(", "))}${p.authors.length > 3 ? " et al." : ""}` : ""}</div></div></li>`;
}

function inYears(pmid: string) {
  const y = run?.papers[pmid]?.year;
  return !years || (y != null && y >= years[0] && y <= years[1]);
}

function renderDetails() {
  const el = $("panel-details");
  if (!run) {
    el.innerHTML = `<p class="muted">Run a search to see details here.</p>`;
    return;
  }
  if (selected) return renderNode(el, selected);
  if (focusCluster !== null) return renderCluster(el, focusCluster);
  renderOverview(el);
}

function renderOverview(el: HTMLElement) {
  const { manifest, graph } = run!;
  const warnings = manifest.warnings.map((w) => `<div class="banner warn">${esc(w)}</div>`).join("");
  el.innerHTML = `
    <h2 class="panel-title">${esc(manifest.query)}</h2>
    <dl class="stats">
      <div><dt>PubMed matches</dt><dd>${fmt(manifest.pubmed_count)}</dd></div>
      <div><dt>Papers mapped</dt><dd>${fmt(manifest.parsed)}</dd></div>
      <div><dt>Entities shown</dt><dd>${fmt(graph.nodes.length)}</dd></div>
      <div><dt>Clusters</dt><dd>${fmt(graph.clusters.length)}</dd></div>
    </dl>
    ${warnings}
    ${unlinkedList()}
    <h3>Clusters</h3>
    <p class="muted small">Named by their most distinctive subject terms. Select one to focus on it.</p>
    <ol class="cluster-list">
      ${graph.clusters
        .map(
          (c) => `<li><button type="button" class="row-button" data-cluster="${c.id}">
            <span>${esc(c.label || `Cluster ${c.id + 1}`)}</span><span class="muted num">${c.size}</span></button></li>`,
        )
        .join("")}
    </ol>
    <h3>Reading the map</h3>
    <ul class="reading small">
      <li>Each mark is an entity; ${run!.graph.nodes.some((n) => n.type === "mesh") ? "its shape and color give its type, and" : ""} its area grows with the number of papers it appears on.</li>
      <li>Lines join entities that share papers; darker lines are stronger links. Weak links appear when you select a node.</li>
      <li>Gray regions are clusters found from the links, not from position.</li>
      <li>Hollow, dashed marks moved between layout runs. Trust their links more than their location.</li>
    </ul>`;
  el.querySelectorAll<HTMLButtonElement>("[data-cluster]").forEach((b) =>
    b.addEventListener("click", () => {
      focusCluster = Number(b.dataset.cluster);
      map.setFocusCluster(focusCluster);
      renderDetails();
    }),
  );
}

function unlinkedList(): string {
  const step = run!.manifest.steps.find((s) => s.name === "Network");
  const list = (step?.counts.unlinked ?? []) as { label: string; type: EntityType; count: number }[];
  if (!list.length) return "";
  return `<details class="small"><summary>${list.length} unlinked entit${list.length === 1 ? "y" : "ies"} not drawn</summary>
    <ul class="entity-list">${list
      .map((u) => `<li class="row-button">${iconHtml(u.type, 16)}<span class="grow">${esc(u.label)}</span><span class="muted num">${u.count}</span></li>`)
      .join("")}</ul></details>`;
}

function renderCluster(el: HTMLElement, id: number) {
  const c = run!.graph.clusters[id];
  const members = run!.graph.nodes
    .filter((n) => n.cluster === id && shownTypes.has(n.type))
    .sort((a, b) => map.visibleCount(b.id) - map.visibleCount(a.id));
  el.innerHTML = `
    <button type="button" class="link back">← All clusters</button>
    <h2 class="panel-title">${esc(c.label || `Cluster ${id + 1}`)}</h2>
    <p class="muted small">${c.size} entities. The name joins the subject terms that are most frequent and most tied to this cluster; check it against the members below.</p>
    <h3>Members</h3>
    ${entityList(members)}`;
  el.querySelector(".back")!.addEventListener("click", () => {
    focusCluster = null;
    map.setFocusCluster(null);
    renderDetails();
  });
  wireEntityButtons(el);
}

function entityList(nodes: GraphNode[], extra?: (n: GraphNode) => string): string {
  return `<ul class="entity-list">${nodes
    .map(
      (n) => `<li><button type="button" class="row-button" data-node="${esc(n.id)}">
        ${iconHtml(n.type, 18, n.stability < LOW_STABILITY)}<span class="grow">${esc(n.label)}</span>
        <span class="muted num">${extra ? extra(n) : map.visibleCount(n.id)}</span></button></li>`,
    )
    .join("")}</ul>`;
}

function wireEntityButtons(el: HTMLElement) {
  el.querySelectorAll<HTMLButtonElement>("[data-node]").forEach((b) =>
    b.addEventListener("click", () => {
      map.select(b.dataset.node!);
      map.zoomToNode(b.dataset.node!);
    }),
  );
}

function renderNode(el: HTMLElement, n: GraphNode) {
  const style = TYPE_STYLES[n.type];
  const pmids = n.pmids.filter(inYears).sort((a, b) => (run!.papers[b]?.year ?? 0) - (run!.papers[a]?.year ?? 0));
  const neighbors = map.neighbors(n.id).sort((a, b) => b.shared.length - a.shared.length || b.edge.weight - a.edge.weight);
  const cluster = run!.graph.clusters[n.cluster];
  const shown = showAllPapers ? pmids : pmids.slice(0, 15);
  const ext = externalLink(n);
  el.innerHTML = `
    <button type="button" class="link back">← Overview</button>
    <div class="node-head">${iconHtml(n.type, 28)}<span class="muted">${style.label}</span></div>
    <h2 class="panel-title">${esc(n.label)}</h2>
    ${ext ? `<p class="small"><a href="${esc(ext.url)}" target="_blank" rel="noopener">${esc(ext.text)}</a></p>` : ""}
    <dl class="stats">
      <div><dt>Papers${years ? " in range" : ""}</dt><dd>${fmt(pmids.length)}</dd></div>
      <div><dt>Linked entities</dt><dd>${fmt(neighbors.length)}</dd></div>
      <div><dt>Layout stability</dt><dd>${n.stability.toFixed(2)}</dd></div>
    </dl>
    ${n.stability < LOW_STABILITY ? `<div class="banner note">This node's position changed between layout runs. Its links are reliable; its exact location is not.</div>` : ""}
    ${cluster ? `<p class="small">Cluster: <button type="button" class="link" data-goto-cluster="${cluster.id}">${esc(cluster.label)}</button></p>` : ""}
    ${n.variants.length ? `<details class="small"><summary>Also written as (${n.variants.length})</summary><p>${n.variants.map(esc).join("; ")}</p></details>` : ""}
    <h3>Most linked</h3>
    <p class="muted small">Shared papers, most first.</p>
    ${entityList(neighbors.slice(0, 12).map((x) => x.node), (m) => String(neighbors.find((x) => x.node.id === m.id)!.shared.length))}
    <h3>Papers</h3>
    <ol class="paper-list">${shown.map(paperItem).join("")}</ol>
    ${pmids.length > shown.length ? `<button type="button" class="link" id="more-papers">Show all ${fmt(pmids.length)} papers</button>` : ""}`;
  el.querySelector(".back")!.addEventListener("click", () => map.select(null));
  el.querySelector("#more-papers")?.addEventListener("click", () => {
    showAllPapers = true;
    renderDetails();
  });
  el.querySelector("[data-goto-cluster]")?.addEventListener("click", () => {
    map.select(null);
    focusCluster = n.cluster;
    map.setFocusCluster(n.cluster);
    renderDetails();
  });
  wireEntityButtons(el);
}

function externalLink(n: GraphNode): { url: string; text: string } | null {
  if (n.type === "mesh" && n.ui) return { url: `https://meshb.nlm.nih.gov/record/ui?ui=${n.ui}`, text: `MeSH record ${n.ui}` };
  if (n.type === "author" && n.orcid) return { url: `https://orcid.org/${n.orcid}`, text: `ORCID ${n.orcid}` };
  if (n.type === "grant") {
    const id = n.id.replace(/^grant:/, "");
    if (/^[A-Z]\d\d[A-Z]{2}\d{6}$/.test(id))
      return { url: `https://reporter.nih.gov/search/results?query=${encodeURIComponent(id)}`, text: "Look up in NIH RePORTER" };
  }
  return null;
}

// --- entity list (accessible table view of the map) -----------------------------

function renderList() {
  const el = $("panel-list");
  if (!run) {
    el.innerHTML = "";
    return;
  }
  const rows = run.graph.nodes
    .filter((n) => shownTypes.has(n.type) && map.visibleCount(n.id) > 0)
    .sort((a, b) =>
      listSort === "label"
        ? a.label.localeCompare(b.label)
        : listSort === "type"
          ? TYPE_STYLES[a.type].slot - TYPE_STYLES[b.type].slot || map.visibleCount(b.id) - map.visibleCount(a.id)
          : map.visibleCount(b.id) - map.visibleCount(a.id),
    );
  const head = (key: typeof listSort, text: string, cls = "") =>
    `<th scope="col" class="${cls}" aria-sort="${listSort === key ? (key === "count" ? "descending" : "ascending") : "none"}">
      <button type="button" class="th-button" data-sort="${key}">${text}</button></th>`;
  el.innerHTML = `
    <p class="muted small">${fmt(rows.length)} entities${years ? ` with papers in ${years[0]}–${years[1]}` : ""}. Select a row to find it on the map.</p>
    <table class="entity-table">
      <thead><tr>${head("type", "Type")}${head("label", "Name")}${head("count", "Papers", "num")}</tr></thead>
      <tbody>${rows
        .map(
          (n) => `<tr><td>${iconHtml(n.type, 18, n.stability < LOW_STABILITY)} <span class="sr-only">${TYPE_STYLES[n.type].label}</span></td>
          <td><button type="button" class="link" data-node="${esc(n.id)}">${esc(n.label)}</button></td>
          <td class="num">${map.visibleCount(n.id)}</td></tr>`,
        )
        .join("")}</tbody>
    </table>`;
  el.querySelectorAll<HTMLButtonElement>("[data-sort]").forEach((b) =>
    b.addEventListener("click", () => {
      listSort = b.dataset.sort as typeof listSort;
      renderList();
    }),
  );
  wireEntityButtons(el);
}

// --- provenance ("How this map was made") ---------------------------------------

function renderCounts(counts: Record<string, unknown>): string {
  const parts: string[] = [];
  for (const [k, v] of Object.entries(counts)) {
    if (k === "unlinked") {
      const list = v as { label: string }[];
      if (list.length) parts.push(`<li><span class="muted">unlinked, not drawn:</span> ${list.length}</li>`);
      continue;
    }
    const label = k.replace(/_/g, " ");
    if (v && typeof v === "object" && !Array.isArray(v)) {
      const inner = Object.entries(v as Record<string, unknown>).map(([a, b]) => `${a.replace(/_/g, " ")} ${fmtVal(b)}`).join(", ");
      parts.push(`<li><span class="muted">${esc(TYPE_STYLES[k as EntityType]?.plural ?? label)}:</span> ${esc(inner)}</li>`);
    } else {
      parts.push(`<li><span class="muted">${esc(label)}:</span> ${esc(fmtVal(v))}</li>`);
    }
  }
  return `<ul class="counts small">${parts.join("")}</ul>`;
}
function fmtVal(v: unknown): string {
  if (typeof v === "number") return fmt(v);
  if (Array.isArray(v)) return v.map(fmtVal).join(", ");
  return String(v);
}

function logItem(e: LogEntry): string {
  const badge = e.uncertain ? `<span class="badge">Check</span>` : "";
  return `<li class="${e.uncertain ? "uncertain" : ""}">${badge}<strong>${esc(e.label)}</strong>
    <span class="muted"> (${esc(TYPE_STYLES[e.type]?.label ?? e.type)}, ${esc(e.action.replace("_", " "))})</span>
    <div class="small">${esc(e.reason)}</div>
    ${e.variants.length > 1 || e.action !== "unparsed" ? `<div class="small muted">${e.variants.map(esc).join(" · ")}</div>` : ""}</li>`;
}

function renderMethod() {
  const el = $("panel-method");
  if (!run) return;
  const m = run.manifest;
  const id = encodeURIComponent(m.run_id);
  const log = uncertainOnly ? m.normalization.filter((e) => e.uncertain) : m.normalization;
  el.innerHTML = `
    <p class="small">Every choice below changes the map. They are recorded so the map can be checked and reproduced.</p>
    <dl class="kv small">
      <dt>Query</dt><dd><code>${esc(m.query)}</code></dd>
      ${m.query_translation ? `<dt>PubMed read it as</dt><dd><code>${esc(m.query_translation)}</code></dd>` : ""}
      <dt>Source</dt><dd>${esc(m.source)}${m.from_cache ? " (from local cache)" : ""}</dd>
      <dt>Retrieved from PubMed</dt><dd>${m.retrieved_at ? new Date(m.retrieved_at).toLocaleString() : "unknown"}</dd>
      <dt>Map built</dt><dd>${new Date(m.created_at).toLocaleString()}</dd>
    </dl>
    ${m.warnings.map((w) => `<div class="banner warn">${esc(w)}</div>`).join("")}
    <h3>Steps</h3>
    <ol class="steps">${m.steps
      .map((s) => `<li><strong>${esc(s.name)}</strong><div class="small">${esc(s.detail)}</div>${renderCounts(s.counts)}</li>`)
      .join("")}</ol>
    <h3>Name matching</h3>
    <p class="small muted">${m.normalization.filter((e) => e.action === "merged").length} merges,
      ${m.normalization.filter((e) => e.uncertain).length} flagged for checking.</p>
    <label class="check small"><input type="checkbox" id="uncertain-only" ${uncertainOnly ? "checked" : ""}/> Show only items to check</label>
    <ul class="log">${log.slice(0, 300).map(logItem).join("") || `<li class="muted small">Nothing to show.</li>`}</ul>
    ${log.length > 300 ? `<p class="small muted">Showing 300 of ${fmt(log.length)}. Download the manifest for all.</p>` : ""}
    <details class="small"><summary>All parameters</summary><pre>${esc(JSON.stringify(m.parameters, null, 2))}</pre></details>
    <details class="small"><summary>Software</summary><pre>${esc(Object.entries(m.software).map(([k, v]) => `${k} ${v}`).join("\n"))}</pre></details>
    <h3>Download</h3>
    <ul class="downloads small">
      <li><a href="${api.exportUrl(id, "json")}">Manifest and map (JSON)</a></li>
      <li><a href="${api.exportUrl(id, "gexf")}">GEXF</a> for Gephi, with positions</li>
      <li><a href="${api.exportUrl(id, "graphml")}">GraphML</a> for Cytoscape and others</li>
      <li><a href="${api.exportUrl(id, "nodes.csv")}">Entities (CSV)</a> · <a href="${api.exportUrl(id, "edges.csv")}">Links (CSV)</a></li>
    </ul>`;
  el.querySelector("#uncertain-only")!.addEventListener("change", (e) => {
    uncertainOnly = (e.target as HTMLInputElement).checked;
    renderMethod();
  });
}

// --- start ----------------------------------------------------------------------

window.addEventListener("hashchange", () => {
  const id = decodeURIComponent(location.hash.slice(1));
  if (id && id !== run?.manifest.run_id) {
    loadRun(id).catch((err) => showError(String(err)));
  }
});

(async () => {
  renderDetails();
  try {
    defaults = await api.defaults();
    renderTypeOptions();
    await refreshRuns();
    const fromHash = decodeURIComponent(location.hash.slice(1));
    if (fromHash) await loadRun(fromHash);
  } catch (err) {
    showError(`Can't reach the Sciologer server: ${err instanceof Error ? err.message : err}`);
  }
})();
