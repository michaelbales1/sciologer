// The network map: a Canvas 2D renderer with d3-zoom for pan/zoom and a quadtree
// for hit testing.
//
// Design rules this follows (see README "Visual design"):
// - One encoding per channel: position = layout, shape + hue = entity type,
//   area = number of papers, link opacity = link strength. Clusters are neutral
//   background regions, so they don't compete with type for hue.
// - Overview first: weak links hidden, labels only where they fit. Details on demand:
//   hovering or selecting a node shows its neighborhood and opens the side panel.
// - Positions are approximate. Nodes whose neighborhood changed across layout runs
//   are drawn hollow with a dashed outline.

import { polygonHull } from "d3-polygon";
import { quadtree, type Quadtree } from "d3-quadtree";
import { select } from "d3-selection";
import { zoom, zoomIdentity, type ZoomBehavior, type ZoomTransform } from "d3-zoom";
import { cssVar, drawIcon, ICON_SCALE, iconHtml, loadIcons, MIN_ICON_PX, mix, TYPE_STYLES, tracePath, typeColor } from "./encoding";
import type { EntityType, Graph, GraphEdge, GraphNode, PaperInfo } from "./types";

export const LOW_STABILITY = 0.5;

export interface MapFilter {
  types: Set<EntityType>;
  years: [number, number] | null;
  linkShare: number; // 0..1: show this share of links, strongest first
  search: string;
}

export interface MapCallbacks {
  onSelect: (node: GraphNode | null) => void;
  onCluster: (id: number | null) => void;
  onVisibleChange: (visibleNodes: number, visibleEdges: number) => void;
}

interface NodeView {
  node: GraphNode;
  visible: boolean;
  count: number; // papers within the year filter
  sx: number;
  sy: number;
  r: number;
  match: boolean;
}

interface EdgeView {
  edge: GraphEdge;
  a: NodeView;
  b: NodeView;
  rank: number; // 0 = strongest
  shared: string[];
  visible: boolean;
  strength: number; // 0..1 within visible links
}

export class NetworkMap {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private tooltip: HTMLDivElement;
  private zoomer: ZoomBehavior<HTMLCanvasElement, unknown>;
  private transform: ZoomTransform = zoomIdentity;
  private width = 0;
  private height = 0;
  private dpr = 1;
  private graph: Graph | null = null;
  private papers: Record<string, PaperInfo> = {};
  private nodes: NodeView[] = [];
  private byId = new Map<string, NodeView>();
  private edges: EdgeView[] = [];
  private adjacency = new Map<string, EdgeView[]>();
  private tree: Quadtree<NodeView> | null = null;
  private hovered: NodeView | null = null;
  private selected: NodeView | null = null;
  private focusCluster: number | null = null;
  private filter: MapFilter = { types: new Set(), years: null, linkShare: 0.1, search: "" };
  private frame = 0;
  private clusterLabelBoxes: { id: number; x: number; y: number; w: number; h: number }[] = [];

  constructor(private container: HTMLElement, private cb: MapCallbacks) {
    this.canvas = document.createElement("canvas");
    this.canvas.className = "map-canvas";
    this.canvas.tabIndex = 0;
    this.canvas.setAttribute("role", "img");
    this.canvas.setAttribute("aria-label", "Network map. Use the Entities tab for a keyboard-accessible list.");
    this.ctx = this.canvas.getContext("2d")!;
    this.tooltip = document.createElement("div");
    this.tooltip.className = "map-tooltip";
    this.tooltip.hidden = true;
    container.append(this.canvas, this.tooltip);

    this.zoomer = zoom<HTMLCanvasElement, unknown>()
      .scaleExtent([0.5, 12])
      .on("zoom", (e) => {
        this.transform = e.transform;
        this.hideTooltip();
        this.requestDraw();
      });
    select(this.canvas).call(this.zoomer).on("dblclick.zoom", null);

    this.canvas.addEventListener("pointermove", (e) => this.onPointerMove(e));
    this.canvas.addEventListener("pointerleave", () => {
      this.hovered = null;
      this.hideTooltip();
      this.requestDraw();
    });
    this.canvas.addEventListener("click", (e) => this.onClick(e));
    this.canvas.addEventListener("keydown", (e) => {
      if (e.key === "Escape") this.select(null);
    });
    new ResizeObserver(() => this.resize()).observe(container);
    loadIcons(() => this.requestDraw());
    matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => this.requestDraw());
    this.resize();
  }

  setData(graph: Graph, papers: Record<string, PaperInfo>) {
    this.graph = graph;
    this.papers = papers;
    this.nodes = graph.nodes.map((node) => ({ node, visible: true, count: node.count, sx: 0, sy: 0, r: 0, match: false }));
    this.byId = new Map(this.nodes.map((v) => [v.node.id, v]));
    this.maxCount = Math.max(1, ...graph.nodes.map((n) => n.count));
    const sorted = [...graph.edges].sort((a, b) => b.weight - a.weight);
    this.edges = sorted.map((edge, rank) => {
      const a = this.byId.get(edge.source)!;
      const b = this.byId.get(edge.target)!;
      const setB = new Set(b.node.pmids);
      return { edge, a, b, rank, shared: a.node.pmids.filter((p) => setB.has(p)), visible: true, strength: 1 };
    });
    this.adjacency = new Map();
    for (const e of this.edges) {
      for (const end of [e.a, e.b]) {
        const list = this.adjacency.get(end.node.id) ?? [];
        list.push(e);
        this.adjacency.set(end.node.id, list);
      }
    }
    this.selected = null;
    this.hovered = null;
    this.focusCluster = null;
    select(this.canvas).call(this.zoomer.transform, zoomIdentity);
    this.applyFilter();
  }

  setFilter(filter: Partial<MapFilter>) {
    this.filter = { ...this.filter, ...filter };
    this.applyFilter();
  }

  private inYears(pmid: string): boolean {
    const yr = this.filter.years;
    if (!yr) return true;
    const y = this.papers[pmid]?.year;
    return y != null && y >= yr[0] && y <= yr[1];
  }

  private applyFilter() {
    const q = this.filter.search.trim().toLowerCase();
    for (const v of this.nodes) {
      v.count = this.filter.years ? v.node.pmids.filter((p) => this.inYears(p)).length : v.node.count;
      v.visible = this.filter.types.has(v.node.type) && v.count > 0;
      v.match = q.length > 0 && v.node.label.toLowerCase().includes(q);
    }
    const candidates = this.edges.filter(
      (e) => e.a.visible && e.b.visible && (!this.filter.years || e.shared.some((p) => this.inYears(p))),
    );
    const keep = Math.ceil(candidates.length * this.filter.linkShare);
    const maxW = candidates[0]?.edge.weight ?? 1;
    const minW = candidates[Math.max(0, keep - 1)]?.edge.weight ?? 0;
    for (const e of this.edges) e.visible = false;
    candidates.forEach((e, i) => {
      e.visible = i < keep;
      e.strength = maxW > minW ? (e.edge.weight - minW) / (maxW - minW) : 1;
    });
    if (this.selected && !this.selected.visible) this.select(null);
    this.cb.onVisibleChange(
      this.nodes.filter((v) => v.visible).length,
      this.edges.filter((e) => e.visible).length,
    );
    this.requestDraw();
  }

  /** All links of a node that pass the type and year filters, strongest first. */
  neighbors(id: string): { node: GraphNode; edge: GraphEdge; shared: string[] }[] {
    return (this.adjacency.get(id) ?? [])
      .map((e) => ({ e, other: e.a.node.id === id ? e.b : e.a }))
      .filter(({ e, other }) => other.visible && (!this.filter.years || e.shared.some((p) => this.inYears(p))))
      .map(({ e, other }) => ({ node: other.node, edge: e.edge, shared: e.shared.filter((p) => this.inYears(p)) }));
  }

  visibleCount(id: string): number {
    return this.byId.get(id)?.count ?? 0;
  }

  select(id: string | null) {
    const v = id ? this.byId.get(id) ?? null : null;
    this.selected = v && v.visible ? v : null;
    if (this.selected) this.focusCluster = null;
    this.cb.onSelect(this.selected?.node ?? null);
    this.requestDraw();
  }

  setFocusCluster(id: number | null) {
    this.focusCluster = id;
    if (id !== null && this.selected) {
      this.selected = null;
      this.cb.onSelect(null);
    }
    this.requestDraw();
  }

  zoomToNode(id: string) {
    const v = this.byId.get(id);
    if (!v) return;
    const [wx, wy] = this.world(v.node);
    const k = Math.max(this.transform.k, 2.5);
    const t = zoomIdentity.translate(this.width / 2 - wx * k, this.height / 2 - wy * k).scale(k);
    select(this.canvas).call(this.zoomer.transform, t);
  }

  resetView() {
    select(this.canvas).call(this.zoomer.transform, zoomIdentity);
  }

  private resize() {
    const rect = this.container.getBoundingClientRect();
    this.width = Math.max(1, rect.width);
    this.height = Math.max(1, rect.height);
    this.dpr = window.devicePixelRatio || 1;
    this.canvas.width = Math.round(this.width * this.dpr);
    this.canvas.height = Math.round(this.height * this.dpr);
    this.canvas.style.width = `${this.width}px`;
    this.canvas.style.height = `${this.height}px`;
    this.requestDraw();
  }

  requestDraw() {
    if (this.frame) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      this.draw();
    });
  }

  /** Graph coordinates ([-1, 1]) to unzoomed canvas coordinates. */
  private world(n: GraphNode): [number, number] {
    const pad = 48;
    const s = Math.min(this.width, this.height) / 2 - pad;
    return [this.width / 2 + n.x * s, this.height / 2 - n.y * s];
  }

  private maxCount = 1;

  private radius(count: number): number {
    // Area proportional to papers (r ∝ sqrt(count)), relative to the largest node so the
    // biggest marks never swamp the map. Grows gently with zoom.
    const zoomGrow = Math.min(2, Math.sqrt(Math.max(1, this.transform.k)));
    const base = Math.min(this.width, this.height);
    const rMax = Math.max(10, Math.min(22, base / 40));
    return (2.5 + (rMax - 2.5) * Math.sqrt(count / this.maxCount)) * zoomGrow;
  }

  private neighborhood(): Set<string> | null {
    const focus = this.selected ?? this.hovered;
    if (!focus) return null;
    const ids = new Set([focus.node.id]);
    for (const e of this.adjacency.get(focus.node.id) ?? []) {
      const other = e.a === focus ? e.b : e.a;
      if (other.visible && (!this.filter.years || e.shared.some((p) => this.inYears(p)))) ids.add(other.node.id);
    }
    return ids;
  }

  private draw() {
    const { ctx, transform: t } = this;
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.fillStyle = cssVar("--surface-1");
    ctx.fillRect(0, 0, this.width, this.height);
    if (!this.graph) return;

    const surface = cssVar("--surface-1");
    const ink = cssVar("--text-primary");
    const ink2 = cssVar("--text-secondary");
    const edgeInk = cssVar("--edge-ink");
    const regionFill = cssVar("--region");
    const regionFocus = cssVar("--region-focus");

    for (const v of this.nodes) {
      const [wx, wy] = this.world(v.node);
      [v.sx, v.sy] = t.apply([wx, wy]);
      v.r = this.radius(v.count);
    }
    const visible = this.nodes.filter((v) => v.visible);
    this.tree = quadtree<NodeView>()
      .x((v) => v.sx)
      .y((v) => v.sy)
      .addAll(visible);

    const hood = this.neighborhood();
    const focusC = this.focusCluster;
    const dimmed = (v: NodeView) =>
      (hood !== null && !hood.has(v.node.id)) || (focusC !== null && v.node.cluster !== focusC);

    // 1. Cluster regions: a thick, round-joined hull stroke reads as a soft area.
    const members = new Map<number, NodeView[]>();
    for (const v of visible) {
      const list = members.get(v.node.cluster) ?? [];
      list.push(v);
      members.set(v.node.cluster, list);
    }
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    const regionPad = 18 + 4 * Math.sqrt(t.k);
    for (const [cid, list] of members) {
      if (list.length < 3) continue;
      // Wrap the cluster's core, not every member: hubs and bridging nodes sit far
      // from the rest and would stretch each region across the whole map.
      const cx = list.reduce((s, v) => s + v.sx, 0) / list.length;
      const cy = list.reduce((s, v) => s + v.sy, 0) / list.length;
      const dist = list.map((v) => Math.hypot(v.sx - cx, v.sy - cy)).sort((a, b) => a - b);
      const cut = dist[Math.floor((dist.length - 1) * 0.7)];
      const core = list.filter((v) => Math.hypot(v.sx - cx, v.sy - cy) <= cut);
      if (core.length < 3) continue;
      const pts = core.map((v) => [v.sx, v.sy] as [number, number]);
      const hull = pts.length >= 3 ? polygonHull(pts) : pts;
      if (!hull) continue;
      ctx.beginPath();
      hull.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
      ctx.closePath();
      ctx.fillStyle = ctx.strokeStyle = focusC === cid ? regionFocus : regionFill;
      ctx.globalAlpha = focusC !== null && focusC !== cid ? 0.4 : 1;
      ctx.lineWidth = regionPad * 2;
      ctx.stroke();
      ctx.fill();
    }
    ctx.globalAlpha = 1;

    // 2. Links. Hairlines; strength sets opacity. The focused neighborhood's links
    //    are always shown, including weak ones hidden by the link filter.
    ctx.lineWidth = 1;
    ctx.strokeStyle = edgeInk;
    for (const e of this.edges) {
      const inHood = hood !== null && hood.has(e.a.node.id) && hood.has(e.b.node.id) &&
        (e.a === (this.selected ?? this.hovered) || e.b === (this.selected ?? this.hovered));
      if (!e.visible && !inHood) continue;
      if (!e.a.visible || !e.b.visible) continue;
      let alpha = 0.06 + 0.3 * e.strength;
      if (hood !== null) alpha = inHood ? 0.35 + 0.5 * e.strength : 0.03;
      else if (focusC !== null) alpha = e.a.node.cluster === focusC && e.b.node.cluster === focusC ? alpha * 1.6 : 0.03;
      ctx.globalAlpha = Math.min(1, alpha);
      ctx.beginPath();
      ctx.moveTo(e.a.sx, e.a.sy);
      ctx.lineTo(e.b.sx, e.b.sy);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;

    // 3. Nodes, small on top of large so small ones stay reachable. Marks big enough
    //    for their pictogram to read get it; smaller ones get the matching shape.
    const order = [...visible].sort((a, b) => b.r - a.r);
    for (const v of order) {
      const style = TYPE_STYLES[v.node.type];
      const color = typeColor(v.node.type);
      const unstable = v.node.stability < LOW_STABILITY;
      const iconSize = v.r * ICON_SCALE;
      ctx.globalAlpha = dimmed(v) ? 0.18 : 1;
      let drewIcon = false;
      if (iconSize >= MIN_ICON_PX) {
        // A surface disc behind the pictogram hides links passing underneath.
        ctx.beginPath();
        ctx.arc(v.sx, v.sy, iconSize * 0.42, 0, Math.PI * 2);
        ctx.fillStyle = surface;
        ctx.fill();
        drewIcon = drawIcon(ctx, v.node.type, v.sx, v.sy, iconSize, unstable ? surface : mix(color, surface, 0.22), color);
        if (drewIcon && unstable) {
          ctx.setLineDash([3, 2]);
          ctx.lineWidth = 1.5;
          ctx.strokeStyle = color;
          ctx.stroke();
          ctx.setLineDash([]);
        }
      }
      if (!drewIcon) {
        tracePath(ctx, style.shape, v.sx, v.sy, v.r);
        if (unstable) {
          ctx.fillStyle = surface;
          ctx.fill();
          ctx.setLineDash([3, 2]);
          ctx.lineWidth = 1.5;
          ctx.strokeStyle = color;
          ctx.stroke();
          ctx.setLineDash([]);
        } else {
          ctx.fillStyle = color;
          ctx.fill();
          // Surface ring keeps overlapping marks separable.
          ctx.lineWidth = 1.5;
          ctx.strokeStyle = surface;
          ctx.stroke();
        }
      }
      if (v === this.selected || v.match) {
        ctx.beginPath();
        ctx.arc(v.sx, v.sy, (drewIcon ? iconSize * 0.5 : v.r * 1.3) + 3, 0, Math.PI * 2);
        ctx.lineWidth = 2;
        ctx.strokeStyle = ink;
        ctx.stroke();
      }
    }
    ctx.globalAlpha = 1;

    // 4. Labels, placed greedily without collisions: cluster names first, then the
    //    focused neighborhood and search matches, then the largest nodes.
    const placed: { x: number; y: number; w: number; h: number }[] = [];
    const fits = (b: { x: number; y: number; w: number; h: number }) =>
      b.x >= 2 && b.y >= 2 && b.x + b.w <= this.width - 2 && b.y + b.h <= this.height - 2 &&
      !placed.some((p) => b.x < p.x + p.w && b.x + b.w > p.x && b.y < p.y + p.h && b.y + b.h > p.y);
    for (const v of visible) placed.push({ x: v.sx - v.r, y: v.sy - v.r, w: v.r * 2, h: v.r * 2 });

    const font = getComputedStyle(document.body).fontFamily;
    this.clusterLabelBoxes = [];
    if (hood === null) {
      ctx.font = `600 13px ${font}`;
      ctx.textBaseline = "middle";
      for (const cluster of this.graph.clusters) {
        const list = members.get(cluster.id);
        if (!list || list.length < 3) continue;
        const cx = list.reduce((s, v) => s + v.sx, 0) / list.length;
        const top = Math.min(...list.map((v) => v.sy - v.r));
        const text = truncate(cluster.label, 46);
        const w = ctx.measureText(text).width + 12;
        const box = { x: cx - w / 2, y: top - regionPad - 4, w, h: 20 };
        if (!fits(box)) {
          box.y = list.reduce((s, v) => s + v.sy, 0) / list.length - 10;
          if (!fits(box)) continue;
        }
        placed.push(box);
        this.clusterLabelBoxes.push({ id: cluster.id, ...box });
        ctx.globalAlpha = focusC !== null && focusC !== cluster.id ? 0.35 : 1;
        haloText(ctx, text, box.x + 6, box.y + box.h / 2, ink2, surface);
      }
      ctx.globalAlpha = 1;
    }

    ctx.font = `12px ${font}`;
    ctx.textBaseline = "middle";
    const budget = hood ? 40 : Math.round(12 + 18 * t.k);
    const ranked = [...visible].sort((a, b) => {
      const pa = (a === this.selected ? 4 : 0) + (hood?.has(a.node.id) ? 2 : 0) + (a.match ? 3 : 0);
      const pb = (b === this.selected ? 4 : 0) + (hood?.has(b.node.id) ? 2 : 0) + (b.match ? 3 : 0);
      return pb - pa || b.count - a.count;
    });
    let shown = 0;
    for (const v of ranked) {
      if (shown >= budget) break;
      if (hood && !hood.has(v.node.id)) continue;
      if (focusC !== null && v.node.cluster !== focusC && !v.match) continue;
      const text = truncate(v.node.label, 32);
      const w = ctx.measureText(text).width + 6;
      const box = { x: v.sx + v.r + 3, y: v.sy - 8, w, h: 16 };
      // Remove this node's own footprint before testing so its label can sit beside it.
      const self = placed.findIndex((p) => p.x === v.sx - v.r && p.y === v.sy - v.r);
      const own = self >= 0 ? placed.splice(self, 1)[0] : null;
      let ok = fits(box);
      if (!ok) {
        box.x = v.sx - v.r - 3 - w;
        ok = fits(box);
      }
      if (own) placed.push(own);
      if (!ok) continue;
      placed.push(box);
      haloText(ctx, text, box.x + 3, v.sy, ink, surface);
      shown++;
    }
  }

  private hit(e: PointerEvent | MouseEvent): NodeView | null {
    if (!this.tree) return null;
    const rect = this.canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    let best: NodeView | null = null;
    let bestD = Infinity;
    // Hit targets are larger than the marks.
    this.tree.visit((node, x0, y0, x1, y1) => {
      if (!("length" in node)) {
        let leaf: typeof node | undefined = node;
        while (leaf) {
          const v = leaf.data;
          const d = Math.hypot(v.sx - x, v.sy - y) - v.r;
          if (d < 8 && d < bestD) {
            best = v;
            bestD = d;
          }
          leaf = leaf.next;
        }
      }
      return x0 > x + 40 || x1 < x - 40 || y0 > y + 40 || y1 < y - 40;
    });
    return best;
  }

  private clusterLabelAt(e: MouseEvent): number | null {
    const rect = this.canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const box = this.clusterLabelBoxes.find((b) => x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h);
    return box ? box.id : null;
  }

  private onPointerMove(e: PointerEvent) {
    const v = this.hit(e);
    const overLabel = !v && this.clusterLabelAt(e) !== null;
    this.canvas.style.cursor = v || overLabel ? "pointer" : "grab";
    if (v !== this.hovered) {
      this.hovered = v;
      this.requestDraw();
    }
    if (v) this.showTooltip(v, e);
    else this.hideTooltip();
  }

  private onClick(e: MouseEvent) {
    const v = this.hit(e);
    if (v) {
      this.select(v === this.selected ? null : v.node.id);
      return;
    }
    const cid = this.clusterLabelAt(e);
    if (cid !== null) {
      const next = this.focusCluster === cid ? null : cid;
      this.setFocusCluster(next);
      this.cb.onCluster(next);
      return;
    }
    if (this.selected) this.select(null);
    else if (this.focusCluster !== null) {
      this.setFocusCluster(null);
      this.cb.onCluster(null);
    }
  }

  private showTooltip(v: NodeView, e: PointerEvent) {
    const n = v.node;
    const style = TYPE_STYLES[n.type];
    const cluster = this.graph?.clusters[n.cluster];
    const links = this.neighbors(n.id).length;
    this.tooltip.innerHTML = `
      <div class="tt-title">${iconHtml(n.type, 16)} ${esc(n.label)}</div>
      <div class="tt-row"><span>${style.label}</span><span>${v.count} paper${v.count === 1 ? "" : "s"} · ${links} link${links === 1 ? "" : "s"}</span></div>
      ${cluster ? `<div class="tt-muted">Cluster: ${esc(truncate(cluster.label, 60))}</div>` : ""}
      ${n.stability < LOW_STABILITY ? `<div class="tt-note">Position varies between layout runs (stability ${n.stability.toFixed(2)}). Read its links, not its location.</div>` : ""}`;
    this.tooltip.hidden = false;
    const rect = this.container.getBoundingClientRect();
    const tw = this.tooltip.offsetWidth;
    const th = this.tooltip.offsetHeight;
    let x = e.clientX - rect.left + 14;
    let y = e.clientY - rect.top + 14;
    if (x + tw > rect.width - 8) x = e.clientX - rect.left - tw - 14;
    if (y + th > rect.height - 8) y = e.clientY - rect.top - th - 14;
    this.tooltip.style.transform = `translate(${Math.max(4, x)}px, ${Math.max(4, y)}px)`;
  }

  private hideTooltip() {
    this.tooltip.hidden = true;
  }
}

function haloText(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, color: string, halo: string) {
  ctx.lineWidth = 3;
  ctx.strokeStyle = halo;
  ctx.lineJoin = "round";
  ctx.strokeText(text, x, y);
  ctx.fillStyle = color;
  ctx.fillText(text, x, y);
}

export function truncate(s: string, n: number): string {
  return s.length > n ? s.slice(0, n - 1).trimEnd() + "…" : s;
}

export function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}
