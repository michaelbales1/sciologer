// Studies page: compare the queries of a batch study (one journal, department or
// author list per query). A bar chart shows one measure across queries, with its
// definition beside it; the table holds every measure; each query opens its maps.

import { api } from "./api";
import { esc } from "./map";
import type { StudyQueryStatus, StudyStatus } from "./types";

const $ = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T;

// Measures that depend strongly on network size, or are only meaningful with context.
const CAVEATS: Record<string, string> = {
  density: "Density falls as networks grow, so compare sets of similar size.",
  mean_path_length:
    "Computed within each set's largest connected group; a short path in a tiny group isn't comparable. Check 'Largest component'.",
  modularity: "Very fragmented networks (many small groups) score high almost automatically.",
  components: "Grows with the number of authors; compare as a share where possible.",
  degree_assortativity: "Unstable in small or highly fragmented networks.",
};

const isShare = (key: string) => key.endsWith("_share") || key === "affiliation_coverage";

function fmtValue(key: string, v: number | null | undefined): string {
  if (v == null) return "–";
  if (isShare(key)) return `${(v * 100).toFixed(1)}%`;
  if (Number.isInteger(v)) return v.toLocaleString();
  return Math.abs(v) >= 100 ? v.toFixed(0) : Math.abs(v) >= 10 ? v.toFixed(1) : v.toFixed(3);
}

export class StudiesPage {
  private study: StudyStatus | null = null;
  private measure = "largest_component_share";
  private poll = 0;
  private tooltip: HTMLDivElement;

  constructor(private openRun: (runId: string) => void) {
    this.tooltip = document.createElement("div");
    this.tooltip.className = "map-tooltip";
    this.tooltip.hidden = true;
    document.body.append(this.tooltip);
    $("study-select").addEventListener("change", (e) => this.load((e.target as HTMLSelectElement).value));
  }

  async show() {
    const list = await api.studies();
    const sel = $("study-select") as HTMLSelectElement;
    if (!list.length) {
      sel.innerHTML = "";
      $("study-csv").hidden = true;
      $("study-body").innerHTML = `
        <div class="study-empty">
          <h2>No studies yet</h2>
          <p>A study runs a list of PubMed queries with the same settings, then compares their collaboration networks. Create a study file, then run it from Terminal:</p>
          <pre>uv run sciologer batch examples/informatics-journals-2021-2026.yaml</pre>
          <p class="muted">The example compares three journals. Copy it, edit the queries, and run your own. Progress is saved after each query, so you can come back later.</p>
        </div>`;
      return;
    }
    const current = this.study?.id ?? list[list.length - 1].id;
    sel.innerHTML = list
      .map((s) => `<option value="${esc(s.id)}">${esc(s.name)} · ${s.done}/${s.queries} done${s.failed ? `, ${s.failed} failed` : ""}</option>`)
      .join("");
    sel.value = list.some((s) => s.id === current) ? current : list[0].id;
    await this.load(sel.value);
  }

  hide() {
    window.clearTimeout(this.poll);
    this.tooltip.hidden = true;
  }

  private async load(id: string) {
    window.clearTimeout(this.poll);
    this.study = await api.study(id);
    ($("study-csv") as HTMLAnchorElement).href = api.studyCsvUrl(id);
    $("study-csv").hidden = false;
    this.render();
    // While a batch is running, refresh every few seconds.
    if (this.study.queries.some((q) => q.state === "running" || q.state === "pending")) {
      this.poll = window.setTimeout(() => {
        if (!$("studies-page").hidden) this.load(id);
      }, 5000);
    }
  }

  private render() {
    const st = this.study!;
    const info = st.metric_info;
    const keys = Object.keys(info);
    if (!keys.includes(this.measure)) this.measure = keys[0];
    const done = st.queries.filter((q) => q.state === "done").length;
    const failed = st.queries.filter((q) => q.state === "error").length;
    const running = st.queries.find((q) => q.state === "running");
    const s = st.settings;
    const settingsText = [
      s.max_records === "all" ? "all matching papers" : `first ${s.max_records} papers`,
      s.sample ? `random sample of ${s.sample} when larger (seed ${s.sample_seed})` : "",
      `views: ${(s.views ?? []).join(", ")}`,
      `papers with more than ${s.metrics?.max_authors_per_paper ?? 100} authors left out of network measures`,
    ].filter(Boolean).join(" · ");

    $("study-body").innerHTML = `
      <h1 class="study-title">${esc(st.name)}</h1>
      ${st.description ? `<p class="study-desc">${esc(st.description)}</p>` : ""}
      <p class="small muted">${done} of ${st.queries.length} queries done${failed ? ` · <span class="error-ink">${failed} failed</span>` : ""}${running ? ` · now running: ${esc(running.label)}` : ""} · ${esc(settingsText)}</p>
      ${failed ? `<div class="banner error small">Failed queries: ${st.queries.filter((q) => q.state === "error").map((q) => `<strong>${esc(q.label)}</strong> (${esc(q.error ?? "error")})`).join("; ")}. Run the batch command again to retry them.</div>` : ""}
      <section class="study-chart-wrap">
        <div class="study-chart-head">
          <label>Measure
            <select id="measure-select">${keys.map((k) => `<option value="${k}" ${k === this.measure ? "selected" : ""}>${esc(info[k].label)}</option>`).join("")}</select>
          </label>
        </div>
        <p class="small">${esc(info[this.measure].description)}</p>
        ${CAVEATS[this.measure] ? `<p class="small muted">Note: ${esc(CAVEATS[this.measure])}</p>` : ""}
        <div id="study-chart" class="study-chart" role="img" aria-label="${esc(info[this.measure].label)} by query"></div>
      </section>
      <h2 class="study-sub">All measures</h2>
      <p class="small muted">Computed on each set's complete co-authorship network (every author, after name matching), not on the drawn map. Select a label to open that query's maps.</p>
      <div class="study-table-wrap">${this.table(keys.filter((k) => k !== "papers"))}</div>`;

    $("measure-select").addEventListener("change", (e) => {
      this.measure = (e.target as HTMLSelectElement).value;
      this.render();
    });
    $("study-body").querySelectorAll<HTMLButtonElement>("[data-run]").forEach((b) =>
      b.addEventListener("click", () => this.openRun(b.dataset.run!)),
    );
    $("study-body").querySelectorAll<HTMLTableCellElement>("th[data-measure]").forEach((th) =>
      th.addEventListener("click", () => {
        this.measure = th.dataset.measure!;
        this.render();
        $("study-chart").scrollIntoView({ block: "nearest", behavior: "smooth" });
      }),
    );
    this.chart();
  }

  private papersCell(q: StudyQueryStatus): string {
    if (q.retrieved == null) return "–";
    const base = q.retrieved.toLocaleString();
    if (q.sampled_from) return `${base} <span class="muted small">sample of ${q.sampled_from.toLocaleString()}</span>`;
    if (q.pubmed_count != null && q.pubmed_count > q.retrieved)
      return `${base} <span class="muted small">of ${q.pubmed_count.toLocaleString()}</span>`;
    return base;
  }

  private table(keys: string[]): string {
    const st = this.study!;
    const head = keys
      .map(
        (k) => `<th scope="col" class="num${k === this.measure ? " current" : ""}" data-measure="${k}" title="${esc(st.metric_info[k].description)}">${esc(st.metric_info[k].label)}</th>`,
      )
      .join("");
    const rows = st.queries
      .map((q) => {
        const label = q.run_id
          ? `<button type="button" class="link" data-run="${esc(q.run_id)}" title="Open this query's maps">${esc(q.label)}</button>`
          : esc(q.label);
        const state = q.state === "done" ? "" : `<span class="state state-${q.state}">${q.state === "error" ? "failed" : q.state}</span>`;
        const cells = keys
          .map((k) => `<td class="num${k === this.measure ? " current" : ""}">${fmtValue(k, q.metrics?.[k])}</td>`)
          .join("");
        return `<tr><th scope="row" title="${esc(q.query)}">${label} ${state}</th><td class="num">${this.papersCell(q)}</td>${cells}</tr>`;
      })
      .join("");
    return `<table class="study-table"><thead><tr><th scope="col">Query</th><th scope="col" class="num">Papers</th>${head}</tr></thead><tbody>${rows}</tbody></table>`;
  }

  private chart() {
    const st = this.study!;
    const el = $("study-chart");
    const k = this.measure;
    const rows = st.queries.map((q) => ({ q, v: q.metrics?.[k] ?? null }));
    const vals = rows.map((r) => r.v).filter((v): v is number => v != null);
    if (!vals.length) {
      el.innerHTML = `<p class="muted small">No values yet.</p>`;
      return;
    }
    const lo = Math.min(0, ...vals);
    const hi = Math.max(0, ...vals) || 1;
    const span = hi - lo || 1;
    const zero = ((0 - lo) / span) * 100;
    el.innerHTML = rows
      .map(({ q, v }, i) => {
        const left = v == null ? zero : ((Math.min(0, v) - lo) / span) * 100;
        const width = v == null ? 0 : (Math.abs(v) / span) * 100;
        return `<div class="bar-row" data-i="${i}">
          <span class="bar-label" title="${esc(q.query)}">${esc(q.label)}</span>
          <span class="bar-track">
            ${v == null ? `<span class="bar-missing">${q.state === "done" ? "n/a" : q.state === "error" ? "failed" : q.state}</span>`
              : `<span class="bar${v < 0 ? " neg" : ""}" style="left:${left}%;width:${Math.max(width, 0.4)}%"></span>`}
          </span>
          <span class="bar-value num">${fmtValue(k, v)}</span>
        </div>`;
      })
      .join("");
    el.querySelectorAll<HTMLElement>(".bar-row").forEach((row) => {
      const { q, v } = rows[Number(row.dataset.i)];
      row.addEventListener("pointermove", (e) => {
        const lcc = q.metrics?.largest_component_share;
        this.tooltip.innerHTML = `<div class="tt-title">${esc(q.label)}</div>
          <div>${esc(st.metric_info[k].label)}: <strong>${fmtValue(k, v)}</strong></div>
          <div class="tt-muted">${q.retrieved != null ? `${q.retrieved.toLocaleString()} papers` : ""}${k === "mean_path_length" && lcc != null ? ` · largest component ${fmtValue("largest_component_share", lcc)}` : ""}</div>
          <div class="tt-muted small">${esc(q.query)}</div>`;
        this.tooltip.hidden = false;
        const x = Math.min(e.clientX + 14, window.innerWidth - this.tooltip.offsetWidth - 8);
        const y = Math.min(e.clientY + 14, window.innerHeight - this.tooltip.offsetHeight - 8);
        this.tooltip.style.position = "fixed";
        this.tooltip.style.transform = `translate(${x}px, ${y}px)`;
      });
      row.addEventListener("pointerleave", () => (this.tooltip.hidden = true));
    });
  }
}
