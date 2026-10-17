// Visual encodings for entity types. Each type has a fixed slot: color follows the
// entity type, never its rank, and never changes when other types are filtered out.
//
// Seven hues can't all be told apart in a scatter-like map (validated: below the
// colorblind and normal-vision floors past three hues), so shape is the primary
// identity channel and hue reinforces it. The three default types take the three
// slots that pass all-pairs in both themes.

import type { EntityType } from "./types";

export type Shape = "circle" | "square" | "triangle" | "diamond" | "hexagon" | "cross" | "triangleDown";

export interface TypeStyle {
  label: string;
  plural: string;
  slot: number; // 1-based categorical slot -> --series-N
  shape: Shape;
}

export const TYPE_STYLES: Record<EntityType, TypeStyle> = {
  mesh: { label: "MeSH term", plural: "MeSH terms", slot: 1, shape: "circle" },
  author: { label: "Author", plural: "Authors", slot: 2, shape: "square" },
  journal: { label: "Journal", plural: "Journals", slot: 3, shape: "triangle" },
  substance: { label: "Substance", plural: "Substances", slot: 4, shape: "diamond" },
  institution: { label: "Institution", plural: "Institutions", slot: 5, shape: "hexagon" },
  grant: { label: "Grant", plural: "Grants", slot: 6, shape: "cross" },
  keyword: { label: "Keyword", plural: "Keywords", slot: 7, shape: "triangleDown" },
};

export const TYPE_ORDER = Object.keys(TYPE_STYLES) as EntityType[];

export function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

export function typeColor(type: EntityType): string {
  return cssVar(`--series-${TYPE_STYLES[type].slot}`);
}

/** Trace a shape of "radius" r centered on (x, y). Areas are roughly equal across shapes. */
export function tracePath(ctx: CanvasRenderingContext2D, shape: Shape, x: number, y: number, r: number) {
  ctx.beginPath();
  switch (shape) {
    case "circle":
      ctx.arc(x, y, r, 0, Math.PI * 2);
      break;
    case "square": {
      const s = r * 0.886; // equal area to the circle
      ctx.rect(x - s, y - s, s * 2, s * 2);
      break;
    }
    case "diamond": {
      const s = r * 1.25;
      ctx.moveTo(x, y - s);
      ctx.lineTo(x + s, y);
      ctx.lineTo(x, y + s);
      ctx.lineTo(x - s, y);
      ctx.closePath();
      break;
    }
    case "triangle":
    case "triangleDown": {
      const s = r * 1.35;
      const dir = shape === "triangle" ? 1 : -1;
      ctx.moveTo(x, y - s * dir);
      ctx.lineTo(x + s * 0.866, y + s * 0.5 * dir);
      ctx.lineTo(x - s * 0.866, y + s * 0.5 * dir);
      ctx.closePath();
      break;
    }
    case "hexagon": {
      const s = r * 1.1;
      for (let i = 0; i < 6; i++) {
        const a = (Math.PI / 3) * i + Math.PI / 6;
        const px = x + s * Math.cos(a);
        const py = y + s * Math.sin(a);
        if (i === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      }
      ctx.closePath();
      break;
    }
    case "cross": {
      const s = r * 1.15;
      const w = s * 0.42;
      ctx.moveTo(x - w, y - s);
      ctx.lineTo(x + w, y - s);
      ctx.lineTo(x + w, y - w);
      ctx.lineTo(x + s, y - w);
      ctx.lineTo(x + s, y + w);
      ctx.lineTo(x + w, y + w);
      ctx.lineTo(x + w, y + s);
      ctx.lineTo(x - w, y + s);
      ctx.lineTo(x - w, y + w);
      ctx.lineTo(x - s, y + w);
      ctx.lineTo(x - s, y - w);
      ctx.lineTo(x - w, y - w);
      ctx.closePath();
      break;
    }
  }
}

/** Small inline SVG of a type's mark, for legends and lists. */
export function shapeSvg(type: EntityType, size = 14, hollow = false): string {
  const { shape, slot } = TYPE_STYLES[type];
  const c = size / 2;
  const r = size * 0.36;
  const fill = hollow ? "var(--surface-1)" : `var(--series-${slot})`;
  const stroke = `var(--series-${slot})`;
  const dash = hollow ? ' stroke-dasharray="2 1.5"' : "";
  const attrs = `fill="${fill}" stroke="${stroke}" stroke-width="${hollow ? 1.5 : 0}"${dash}`;
  let d = "";
  const pts = (list: [number, number][]) => "M" + list.map(([x, y]) => `${x.toFixed(2)},${y.toFixed(2)}`).join("L") + "Z";
  switch (shape) {
    case "circle":
      return `<svg width="${size}" height="${size}" aria-hidden="true"><circle cx="${c}" cy="${c}" r="${r}" ${attrs}/></svg>`;
    case "square": {
      const s = r * 0.886;
      d = pts([[c - s, c - s], [c + s, c - s], [c + s, c + s], [c - s, c + s]]);
      break;
    }
    case "diamond": {
      const s = r * 1.25;
      d = pts([[c, c - s], [c + s, c], [c, c + s], [c - s, c]]);
      break;
    }
    case "triangle":
    case "triangleDown": {
      const s = r * 1.35;
      const k = shape === "triangle" ? 1 : -1;
      d = pts([[c, c - s * k], [c + s * 0.866, c + s * 0.5 * k], [c - s * 0.866, c + s * 0.5 * k]]);
      break;
    }
    case "hexagon": {
      const s = r * 1.1;
      d = pts(Array.from({ length: 6 }, (_, i) => {
        const a = (Math.PI / 3) * i + Math.PI / 6;
        return [c + s * Math.cos(a), c + s * Math.sin(a)] as [number, number];
      }));
      break;
    }
    case "cross": {
      const s = r * 1.15;
      const w = s * 0.42;
      d = pts([[c - w, c - s], [c + w, c - s], [c + w, c - w], [c + s, c - w], [c + s, c + w], [c + w, c + w],
        [c + w, c + s], [c - w, c + s], [c - w, c + w], [c - s, c + w], [c - s, c - w], [c - w, c - w]]);
      break;
    }
  }
  return `<svg width="${size}" height="${size}" aria-hidden="true"><path d="${d}" ${attrs}/></svg>`;
}
