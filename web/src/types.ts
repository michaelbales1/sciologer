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
  /** Mark size, when not the paper count (citation map: Relative Citation Ratio). */
  size?: number;
  /** Text map only: how many of the paper's nearest papers by text are near it on the map. */
  neighbors_kept?: number;
  /** Geographic view: where the institution is (x = longitude, y = latitude). */
  place?: string;
  /** t-SNE maps: which island (connected piece of its cluster) the mark is in. */
  island?: number;
}

/** A connected piece of a cluster on a t-SNE map. When a cluster is drawn in several
 *  places, each sizable piece has its own name from its own papers. */
export interface MapIsland {
  id: number;
  cluster: number;
  size: number;
  label?: string;
  terms?: [string, number][];
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
  /** "islands": one labeled region per spatially connected piece of a cluster (t-SNE
   *  maps); default: one region per cluster around its core. */
  regions?: "islands" | "clusters";
  /** Geographic view: x and y are longitude and latitude, drawn on a world map. */
  geo?: boolean;
  /** Islands computed with the map (newer runs); otherwise found in the browser. */
  islands?: MapIsland[];
}

export interface GeoInstitution {
  id: string; // ROR ID (https://ror.org/…)
  label: string;
  city: string | null;
  region: string | null;
  country: string | null; // ISO 3166 alpha-2
  country_name: string | null;
  lat: number;
  lng: number;
  kinds: string[];
  pmids: string[];
  count: number;
  variants: string[];
}

export interface GeoEstimate {
  distinct_affiliations: number;
  planned: number;
  cached: number;
  to_lookup: number;
  seconds: number;
}

export interface GeoMap {
  institutions: GeoInstitution[];
  edges: { source: string; target: string; count: number; weight: number }[];
  countries: { code: string | null; name: string | null; institutions: number; papers: number }[];
  unplaced: { affiliation: string; count: number }[];
  unplaced_countries: Record<string, number>;
}

export interface CitationMapPaper {
  pmid: string;
  x: number;
  y: number;
  cluster: number;
  stability: number;
  rcr: number | null;
  nih_percentile: number | null;
  provisional: boolean;
  citation_count: number;
  is_research_article: boolean | null;
  references_total: number;
  cites: string[];
  cited_by: string[];
  coupled: { pmid: string; shared: number; cosine: number }[];
  island?: number;
}

export interface CitationMap {
  papers: CitationMapPaper[];
  clusters: TextMapCluster[];
  edges: { source: string; target: string; weight: number; shared: number; direct: boolean }[];
  low_stability: number;
  layout_method: "forceatlas2" | "spectral_tsne";
  islands?: MapIsland[];
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
  island?: number;
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
  islands?: MapIsland[];
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
  citations: CitationMap | null;
  geo?: GeoMap | null;
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

export interface StudySummary {
  id: string;
  name: string;
  queries: number;
  done: number;
  failed: number;
  updated_at: string | null;
}

export interface StudyQueryStatus {
  label: string;
  query: string;
  state: "pending" | "running" | "done" | "error";
  run_id?: string;
  error?: string | null;
  pubmed_count?: number;
  retrieved?: number;
  sampled_from?: number;
  retrieved_at?: string;
  finished_at?: string;
  metrics?: Record<string, number | null>;
  warnings?: string[];
}

export interface StudyStatus {
  id: string;
  name: string;
  description: string | null;
  settings: Record<string, any>;
  metric_info: Record<string, { label: string; description: string }>;
  queries: StudyQueryStatus[];
  updated_at?: string;
}
