export type EntityType =
  | "mesh"
  | "author"
  | "journal"
  | "substance"
  | "institution"
  | "grant"
  | "keyword";

/** What a mark on the map stands for: an entity, or (in the text map) a paper. */
export type MarkType = EntityType | "paper";

export interface GraphNode {
  id: string;
  type: MarkType;
  label: string;
  count: number;
  pmids: string[];
  variants: string[];
  cluster: number;
  x: number;
  y: number;
  stability: number;
  orcid?: string;
  ui?: string;
  agency?: string;
  /** Lowercased text that "Find on map" searches; defaults to the label. */
  search?: string;
  /** Authors under fractional counting: sum of 1/(authors on the paper) over their papers. */
  credit?: number;
  /** Text map only: how many of the paper's nearest papers by text are near it on the map. */
  neighbors_kept?: number;
}

export interface GraphEdge {
  source: string;
  target: string;
  weight: number;
  count: number;
}

export interface ClusterInfo {
  id: number;
  size: number;
  label: string;
  top_members: { key: string; label: string; type: MarkType; count: number }[];
}

export interface Graph {
  run_id: string;
  query: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  clusters: ClusterInfo[];
  /** Marks with stability below this are drawn as unstable (default 0.5). */
  low_stability?: number;
  /** All marks the same size (text map: one paper each). */
  uniform_size?: boolean;
}

export interface TextMapPaper {
  pmid: string;
  x: number;
  y: number;
  cluster: number; // -1 = unclustered
  stability: number;
  neighbors: string[];
  similarity: number[];
  neighbors_kept: number;
  title_only: boolean;
}

export interface TextMapCluster {
  id: number;
  size: number;
  label: string;
  terms: [string, number][];
  representative: string[];
}

export interface TextMap {
  papers: TextMapPaper[];
  clusters: TextMapCluster[];
  edges: { source: string; target: string; similarity: number }[];
  low_stability: number;
}

export interface PaperInfo {
  title: string;
  year: number | null;
  journal: string | null;
  doi: string | null;
  url: string;
  authors: string[];
}

export interface LogEntry {
  type: EntityType;
  action: "merged" | "kept_separate" | "unparsed" | "excluded";
  label: string;
  variants: string[];
  uncertain: boolean;
  reason: string;
}

export interface Manifest {
  run_id: string;
  created_at: string;
  software: Record<string, string>;
  query: string;
  query_translation: string | null;
  source: string;
  pubmed_count: number;
  fetched: number;
  parsed: number;
  from_cache: boolean;
  retrieved_at: string | null;
  pmids: string[];
  parameters: Record<string, unknown>;
  steps: { name: string; detail: string; counts: Record<string, unknown> }[];
  normalization: LogEntry[];
  warnings: string[];
}

export interface Run {
  graph: Graph;
  papers: Record<string, PaperInfo>;
  manifest: Manifest;
  text: TextMap | null;
}

export interface RunSummary {
  run_id: string;
  query: string;
  created_at: string;
  papers: number;
  nodes: number;
  edges: number;
}

export interface JobStatus {
  run_id: string;
  state: "queued" | "running" | "done" | "error";
  message: string;
  progress: number;
  error: string | null;
}

export interface Defaults {
  params: Record<string, any>;
  types: EntityType[];
  default_types: EntityType[];
  version: string;
}
