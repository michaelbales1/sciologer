import type { Defaults, JobStatus, Run, RunSummary, StudyStatus, StudySummary } from "./types";

async function json<T>(resp: Response): Promise<T> {
  if (!resp.ok) {
    let detail = `${resp.status} ${resp.statusText}`;
    try {
      const body = await resp.json();
      if (typeof body?.detail === "string") detail = body.detail;
      else if (Array.isArray(body?.detail))
        // FastAPI validation errors: [{loc: ["body", "network", "max_per_type"], msg: "..."}]
        detail = body.detail
          .map((d: { loc?: unknown[]; msg?: string }) => `${(d.loc ?? []).slice(1).join(" › ")}: ${d.msg ?? ""}`)
          .join("; ");
    } catch {
      /* not JSON */
    }
    throw new Error(detail);
  }
  return resp.json() as Promise<T>;
}

export const api = {
  defaults: () => fetch("/api/defaults").then((r) => json<Defaults>(r)),
  runs: () => fetch("/api/runs").then((r) => json<RunSummary[]>(r)),
  studies: () => fetch("/api/studies").then((r) => json<StudySummary[]>(r)),
  study: (id: string) => fetch(`/api/studies/${encodeURIComponent(id)}`).then((r) => json<StudyStatus>(r)),
  studyCsvUrl: (id: string) => `/api/studies/${encodeURIComponent(id)}/metrics.csv`,
  run: (id: string) => fetch(`/api/runs/${encodeURIComponent(id)}`).then((r) => json<Run>(r)),
  status: (id: string) =>
    fetch(`/api/runs/${encodeURIComponent(id)}/status`).then((r) => json<JobStatus>(r)),
  start: (params: Record<string, unknown>) =>
    fetch("/api/runs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params),
    }).then((r) => json<JobStatus>(r)),
  exportUrl: (id: string, fmt: string) => `/api/runs/${encodeURIComponent(id)}/export/${fmt}`,
};
