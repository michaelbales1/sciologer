export type EntityType =
  | "mesh"
  | "author"
  | "journal"
  | "substance"
  | "institution"
  | "grant"
  | "keyword";

export interface GraphNode {
  id: string;
  type: EntityType;
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
  top_members: { key: string; label: string; type: EntityType; count: number }[];
}

export interface Graph {
  run_id: string;
  query: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  clusters: ClusterInfo[];
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
