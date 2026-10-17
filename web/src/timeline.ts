// Papers-per-year histogram. Brushing a range filters the map (linked views).

import { max, rollup } from "d3-array";
import { brushX, type BrushBehavior } from "d3-brush";
import { scaleBand, scaleLinear } from "d3-scale";
import { select, type Selection } from "d3-selection";
import type { PaperInfo } from "./types";

export class Timeline {
  private svg: Selection<SVGSVGElement, unknown, null, undefined>;
  private tooltip: HTMLDivElement;
  private years: number[] = [];
  private counts = new Map<number, number>();
  private brush: BrushBehavior<unknown> | null = null;
  private range: [number, number] | null = null;
  private suppress = false;

  constructor(private el: HTMLElement, private onRange: (r: [number, number] | null) => void) {
    this.svg = select(el).append("svg").attr("class", "timeline-svg").attr("role", "img");
    this.tooltip = document.createElement("div");
    this.tooltip.className = "map-tooltip";
    this.tooltip.hidden = true;
    el.append(this.tooltip);
    new ResizeObserver(() => this.render()).observe(el);
  }

  setPapers(papers: Record<string, PaperInfo>) {
    const ys = Object.values(papers).map((p) => p.year).filter((y): y is number => y != null);
    this.counts = rollup(ys, (v) => v.length, (y) => y);
    const lo = Math.min(...ys);
    const hi = Math.max(...ys);
    this.years = ys.length ? Array.from({ length: hi - lo + 1 }, (_, i) => lo + i) : [];
    this.range = null;
    this.svg.attr("aria-label", ys.length ? `Papers per year, ${lo} to ${hi}` : "No publication years");
    this.render();
  }

  /** Move the selected range by `delta` years, keeping its width, within the data. */
  shift(delta: number) {
    if (!this.range || !this.years.length) return;
    const lo = this.years[0];
    const hi = this.years[this.years.length - 1];
    const width = this.range[1] - this.range[0];
    const start = Math.max(lo, Math.min(hi - width, this.range[0] + delta));
    this.range = [start, start + width];
    this.render();
    this.onRange(this.range);
  }

  get bounds(): [number, number] | null {
    return this.years.length ? [this.years[0], this.years[this.years.length - 1]] : null;
  }

  clear() {
    this.range = null;
    this.render();
    this.onRange(null);
  }

  private render() {
    const width = this.el.clientWidth;
    const height = this.el.clientHeight;
    const m = { top: 8, right: 12, bottom: 20, left: 32 };
    this.svg.attr("width", width).attr("height", height).selectAll("*").remove();
    if (!this.years.length || width < 60) return;

    const x = scaleBand<number>().domain(this.years).range([m.left, width - m.right]).paddingInner(0.12);
    const y = scaleLinear().domain([0, max(this.counts.values()) ?? 1]).nice(3).range([height - m.bottom, m.top]);
    const g = this.svg.append("g");

    // Recessive axes: a baseline, a few y ticks, sparse year labels.
    for (const t of y.ticks(3)) {
      g.append("line").attr("class", "grid").attr("x1", m.left).attr("x2", width - m.right)
        .attr("y1", y(t)).attr("y2", y(t));
      g.append("text").attr("class", "tick").attr("x", m.left - 6).attr("y", y(t)).attr("dy", "0.32em")
        .attr("text-anchor", "end").text(t);
    }
    const step = Math.max(1, Math.ceil(this.years.length / Math.max(2, Math.floor((width - 60) / 44))));
    this.years.forEach((yr, i) => {
      if (i % step && i !== this.years.length - 1) return;
      g.append("text").attr("class", "tick").attr("x", x(yr)! + x.bandwidth() / 2).attr("y", height - 5)
        .attr("text-anchor", "middle").text(yr);
    });

    const inRange = (yr: number) => !this.range || (yr >= this.range[0] && yr <= this.range[1]);
    const bw = x.bandwidth();
    const rad = Math.min(4, bw / 2);
    for (const yr of this.years) {
      const c = this.counts.get(yr) ?? 0;
      if (!c) continue;
      const top = y(c);
      const h = y(0) - top;
      // Rounded data end, square at the baseline.
      const x0 = x(yr)!;
      const r = Math.min(rad, h);
      g.append("path")
        .attr("class", inRange(yr) ? "bar" : "bar bar-out")
        .attr("d", `M${x0},${y(0)}V${top + r}Q${x0},${top} ${x0 + r},${top}H${x0 + bw - r}Q${x0 + bw},${top} ${x0 + bw},${top + r}V${y(0)}Z`);
    }
    g.append("line").attr("class", "baseline").attr("x1", m.left).attr("x2", width - m.right)
      .attr("y1", y(0)).attr("y2", y(0));

    // Brush, snapped to whole years.
    const yearAt = (px: number) => {
      const i = Math.floor((px - m.left) / x.step());
      return this.years[Math.max(0, Math.min(this.years.length - 1, i))];
    };
    this.brush = brushX<unknown>()
      .extent([[m.left, m.top], [width - m.right, height - m.bottom]])
      .on("end", (e) => {
        if (this.suppress) return;
        if (!e.selection) {
          if (this.range) {
            this.range = null;
            this.onRange(null);
            this.render();
          }
          return;
        }
        const [a, b] = e.selection as [number, number];
        const r: [number, number] = [yearAt(a), yearAt(b - 1)];
        this.range = r;
        this.onRange(r);
        this.render();
      });
    const bg = this.svg.append("g").attr("class", "brush").call(this.brush);
    if (this.range) {
      this.suppress = true;
      bg.call(this.brush.move, [x(this.range[0])!, x(this.range[1])! + bw]);
      this.suppress = false;
    }

    // Per-bar hover via the brush overlay (which sits on top).
    bg.select(".overlay")
      .on("pointermove", (e: PointerEvent) => {
        const rect = this.el.getBoundingClientRect();
        const yr = yearAt(e.clientX - rect.left);
        const c = this.counts.get(yr) ?? 0;
        this.tooltip.innerHTML = `<div class="tt-title">${yr}</div><div>${c} paper${c === 1 ? "" : "s"}</div>`;
        this.tooltip.hidden = false;
        const tx = Math.min(e.clientX - rect.left + 12, rect.width - this.tooltip.offsetWidth - 4);
        this.tooltip.style.transform = `translate(${tx}px, ${-this.tooltip.offsetHeight - 6}px)`;
      })
      .on("pointerleave", () => (this.tooltip.hidden = true));
  }
}
