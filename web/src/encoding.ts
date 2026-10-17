// Visual encodings for entity types. Each type has a fixed slot: color follows the
// entity type, never its rank, and never changes when other types are filtered out.
//
// Seven hues can't all be told apart in a scatter-like map (validated: below the
// colorblind and normal-vision floors past three hues), so the type's pictogram is
// the primary identity channel and hue reinforces it. The three default types take
// the three slots that pass all-pairs in both themes.
//
// Pictograms are the original 2008 Sciologer drawings (legacy/drawing), converted to
// line and fill masks by scripts/make_icons.py so they can take theme colors. Where a
// mark is too small for its pictogram to read, it is drawn as a simple shape that
// echoes the pictogram's outline (person → circle, building → house, and so on).

import type { EntityType, MarkType } from "./types";

export type Shape = "circle" | "square" | "triangle" | "diamond" | "house" | "cross" | "tag";
export type Icon = MarkType;

export interface TypeStyle {
  label: string;
  plural: string;
  slot: number; // 1-based categorical slot -> --series-N
  shape: Shape;
}

export const TYPE_STYLES: Record<MarkType, TypeStyle> = {
  mesh: { label: "MeSH term", plural: "MeSH terms", slot: 1, shape: "triangle" },
  author: { label: "Author", plural: "Authors", slot: 2, shape: "circle" },
  journal: { label: "Journal", plural: "Journals", slot: 3, shape: "square" },
  substance: { label: "Substance", plural: "Substances", slot: 4, shape: "diamond" },
  institution: { label: "Institution", plural: "Institutions", slot: 5, shape: "house" },
  grant: { label: "Grant", plural: "Grants", slot: 6, shape: "cross" },
  keyword: { label: "Keyword", plural: "Keywords", slot: 7, shape: "tag" },
  // The text map has one kind of mark, so it takes the first slot.
  paper: { label: "Paper", plural: "Papers", slot: 1, shape: "circle" },
};

export const TYPE_ORDER: EntityType[] = ["mesh", "author", "journal", "substance", "institution", "grant", "keyword"];

/** Below this on-screen size (css px) a pictogram is drawn as its simple shape. */
export const MIN_ICON_PX = 18;
/** Pictogram box side relative to the mark's radius (keeps area ∝ papers). */
export const ICON_SCALE = 2.7;

// --- icon assets ------------------------------------------------------------------

const urls = import.meta.glob<string>("./assets/icons/*.png", {
  eager: true,
  query: "?url",
  import: "default",
});

function iconUrl(icon: Icon, layer: "line" | "fill"): string {
  return urls[`./assets/icons/${icon}-${layer}.png`];
}

const images = new Map<string, HTMLImageElement>();
let onIconsReady: (() => void) | null = null;
let pending = 0;

/** Starts loading the masks. `ready` is called once all have loaded (to redraw). */
export function loadIcons(ready: () => void) {
  onIconsReady = ready;
  for (const [path, url] of Object.entries(urls)) {
    const img = new Image();
    pending++;
    img.onload = img.onerror = () => {
      if (--pending === 0) onIconsReady?.();
    };
    img.src = url;
    images.set(path.replace(/^.*\/|\.png$/g, ""), img);
  }
}

// --- colors -----------------------------------------------------------------------

export function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

export function typeColor(type: MarkType): string {
  return cssVar(`--series-${TYPE_STYLES[type].slot}`);
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace("#", "");
  const n = parseInt(h.length === 3 ? h.replace(/./g, "$&$&") : h, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** `share` of `color` mixed into `base` (both hex). */
export function mix(color: string, base: string, share: number): string {
  const a = hexToRgb(color);
  const b = hexToRgb(base);
  return `rgb(${a.map((v, i) => Math.round(v * share + b[i] * (1 - share))).join(",")})`;
}

// --- canvas drawing ---------------------------------------------------------------

const tinted = new Map<string, HTMLCanvasElement>();

/** A 128px canvas with the icon's fill and lines painted in the given colors. */
function tintedIcon(icon: Icon, fill: string, line: string): HTMLCanvasElement | null {
  const key = `${icon}|${fill}|${line}`;
  const hit = tinted.get(key);
  if (hit) return hit;
  const fillImg = images.get(`${icon}-fill`);
  const lineImg = images.get(`${icon}-line`);
  if (!fillImg?.complete || !lineImg?.complete || !fillImg.naturalWidth) return null;
  const size = fillImg.naturalWidth;
  const out = document.createElement("canvas");
  out.width = out.height = size;
  const ctx = out.getContext("2d")!;
  const layer = (img: HTMLImageElement, color: string) => {
    const c = document.createElement("canvas");
    c.width = c.height = size;
    const g = c.getContext("2d")!;
    g.drawImage(img, 0, 0);
    g.globalCompositeOperation = "source-in";
    g.fillStyle = color;
    g.fillRect(0, 0, size, size);
    ctx.drawImage(c, 0, 0);
  };
  layer(fillImg, fill);
  layer(lineImg, line);
  tinted.set(key, out);
  return out;
}

/** Draw a type's pictogram in a box of side `size` centered on (x, y). False if not loaded. */
export function drawIcon(
  ctx: CanvasRenderingContext2D,
  icon: Icon,
  x: number,
  y: number,
  size: number,
  fill: string,
  line: string,
): boolean {
  const img = tintedIcon(icon, fill, line);
  if (!img) return false;
  ctx.drawImage(img, x - size / 2, y - size / 2, size, size);
  return true;
}

/** Trace a shape of "radius" r centered on (x, y). Areas are roughly equal across shapes. */
export function tracePath(ctx: CanvasRenderingContext2D, shape: Shape, x: number, y: number, r: number) {
  ctx.beginPath();
  const poly = (pts: [number, number][]) => {
    pts.forEach(([px, py], i) => (i ? ctx.lineTo(x + px, y + py) : ctx.moveTo(x + px, y + py)));
    ctx.closePath();
  };
  switch (shape) {
    case "circle":
      ctx.arc(x, y, r, 0, Math.PI * 2);
      break;
    case "square": {
      const w = r * 0.78;
      const h = r * 1.0;
      ctx.rect(x - w, y - h, w * 2, h * 2);
      break;
    }
    case "diamond": {
      const s = r * 1.25;
      poly([[0, -s], [s, 0], [0, s], [-s, 0]]);
      break;
    }
    case "triangle": {
      const s = r * 1.35;
      poly([[0, -s], [s * 0.866, s * 0.5], [-s * 0.866, s * 0.5]]);
      break;
    }
    case "house": {
      const s = r * 1.05;
      poly([[0, -s * 1.1], [s, -s * 0.35], [s, s], [-s, s], [-s, -s * 0.35]]);
      break;
    }
    case "tag": {
      const s = r * 1.1;
      poly([[-s, -s * 0.6], [s * 0.45, -s * 0.6], [s, 0], [s * 0.45, s * 0.6], [-s, s * 0.6]]);
      break;
    }
    case "cross": {
      const s = r * 1.15;
      const w = s * 0.42;
      poly([[-w, -s], [w, -s], [w, -w], [s, -w], [s, w], [w, w], [w, s], [-w, s], [-w, w], [-s, w], [-s, -w], [-w, -w]]);
      break;
    }
  }
}

// --- HTML ---------------------------------------------------------------------------

/** Inline pictogram for legends, lists and panels; colors follow the theme via CSS. */
export function iconHtml(icon: Icon, size = 16, hollow = false): string {
  const color = `var(--series-${TYPE_STYLES[icon].slot})`;
  const mask = (layer: "line" | "fill") => {
    const u = iconUrl(icon, layer);
    return `-webkit-mask-image:url('${u}');mask-image:url('${u}')`;
  };
  return `<span class="icon${hollow ? " hollow" : ""}" style="--c:${color};width:${size}px;height:${size}px" aria-hidden="true">` +
    `<span class="icon-fill" style="${mask("fill")}"></span><span class="icon-line" style="${mask("line")}"></span></span>`;
}
