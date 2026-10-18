// "Geography": institutions from author affiliations on a world map, as a graph for the
// map renderer (x = longitude, y = latitude), plus the world basemap and the side-panel
// overview. Institutions were matched with the Research Organization Registry (ROR) on
// the server; only confident matches are placed, and the rest are counted and listed.

import { feature, mesh } from "topojson-client";
import type { GeometryObject, Topology } from "topojson-specification";
import type { Graph, GraphNode, Run } from "./types";
import { esc } from "./map";

const fmt = (n: number) => n.toLocaleString();

export function buildGeoGraph(run: Run): Graph | null {
  const geo = run.geo;
  if (!geo || !geo.institutions.length) return null;
  const nodes: GraphNode[] = geo.institutions.map((i) => {
    const place = [i.city, i.country_name].filter(Boolean).join(", ");
    return {
      id: i.id,
      type: "institution",
      label: i.label,
      count: i.count,
      pmids: i.pmids,
      variants: i.variants,
      cluster: -1,
      x: i.lng,
      y: i.lat,
      stability: 1,
      place,
      search: [i.label, i.city, i.region, i.country_name, ...i.variants].filter(Boolean).join(" ").toLowerCase(),
    };
  });
  return {
    run_id: run.graph.run_id,
    query: run.graph.query,
    nodes,
    edges: geo.edges.map((e) => ({ source: e.source, target: e.target, weight: e.weight, count: e.count })),
    clusters: [],
    geo: true,
  };
}

// --- basemap: Natural Earth countries (public domain) via world-atlas --------------

export interface Basemap {
  land: GeoJSON.FeatureCollection | GeoJSON.Feature;
  borders: GeoJSON.MultiLineString;
}

const basemaps = new Map<string, Promise<Basemap>>();

/** Coarse (1:110m) outlines for the world view, finer (1:50m) when zoomed in. Loaded on demand. */
export function loadBasemap(detail: "110m" | "50m"): Promise<Basemap> {
  let p = basemaps.get(detail);
  if (!p) {
    const load = detail === "110m" ? import("world-atlas/countries-110m.json") : import("world-atlas/countries-50m.json");
    p = load.then((m) => {
      const topo = (m.default ?? m) as unknown as Topology<{ land: GeometryObject; countries: GeometryObject }>;
      return {
        land: feature(topo, topo.objects.land),
        borders: mesh(topo, topo.objects.countries, (a, b) => a !== b),
      };
    });
    basemaps.set(detail, p);
  }
  return p;
}

// --- side panel -----------------------------------------------------------------

function step(run: Run): Record<string, any> {
  return run.manifest.steps.find((s) => s.name === "Geography")?.counts ?? {};
}

export function geoOverviewHtml(run: Run): string {
  const geo = run.geo!;
  const c = step(run);
  const pct = (a: number, b: number) => (b ? `${Math.round((100 * a) / b)}%` : "–");
  const maxPapers = Math.max(1, ...geo.countries.map((x) => x.papers));
  const unplacedTotal = Object.values(geo.unplaced_countries).reduce((s, n) => s + n, 0);
  return `
    <h2 class="panel-title">${esc(run.manifest.query)}</h2>
    <p class="small muted">Geography: institutions from author affiliations, placed at the city recorded in the Research Organization Registry (ROR). Size shows papers; lines join institutions that share papers.</p>
    <dl class="stats">
      <div><dt>Institutions</dt><dd>${fmt(c.institutions ?? geo.institutions.length)}</dd></div>
      <div><dt>Countries</dt><dd>${fmt(geo.countries.length)}</dd></div>
      <div><dt>Papers placed</dt><dd>${fmt(c.papers_located ?? 0)} <span class="muted small">of ${fmt(c.papers ?? 0)}</span></dd></div>
      <div><dt>Links</dt><dd>${fmt(geo.edges.length)}</dd></div>
    </dl>
    <p class="small muted">${pct(c.matched_affiliations ?? 0, c.looked_up ?? 0)} of ${fmt(c.looked_up ?? 0)} distinct affiliations matched an institution confidently; ${fmt(c.authorships_placed ?? 0)} of ${fmt(c.authorships_with_affiliation ?? 0)} author affiliations are on the map.${(c.institutions_shown ?? 0) < (c.institutions ?? 0) ? ` The ${fmt(c.institutions_shown)} institutions with the most papers are drawn.` : ""}</p>
    <h3>Countries</h3>
    <p class="muted small">Papers with at least one author at an institution in the country. Select one to zoom to it.</p>
    <ol class="country-list">
      ${geo.countries
        .slice(0, 25)
        .map(
          (k) => `<li><button type="button" class="row-button" data-country="${esc(k.code ?? "")}">
            <span class="grow">${esc(k.name ?? "Unknown")}</span>
            <span class="country-bar" aria-hidden="true"><span style="width:${(100 * k.papers) / maxPapers}%"></span></span>
            <span class="muted num">${fmt(k.papers)}</span></button></li>`,
        )
        .join("")}
    </ol>
    ${geo.countries.length > 25 ? `<p class="small muted">and ${geo.countries.length - 25} more (download the institutions CSV for all).</p>` : ""}
    ${geo.unplaced.length
      ? `<details class="small"><summary>${fmt(unplacedTotal)} author affiliations not placed</summary>
          <p class="muted">ROR wasn't confident about these, so they aren't guessed. Most frequent first:</p>
          <ul class="unplaced">${geo.unplaced.map((u) => `<li>${esc(u.affiliation)} <span class="muted num">${u.count}</span></li>`).join("")}</ul></details>`
      : ""}
    <h3>Reading the map</h3>
    <ul class="reading small">
      <li><strong>Position</strong> is the institution's city, from ROR. Institutions in the same city are spread around it, largest in the middle.</li>
      <li><strong>Size</strong> is the number of papers with an author there. Lines join institutions that share papers; darker lines share more.</li>
      <li>Only confident registry matches are drawn, so institutions with unusual affiliation text may be missing. The count above says how much is covered.</li>
    </ul>`;
}
