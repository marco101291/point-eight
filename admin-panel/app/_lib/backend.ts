// Shared by both the API routes under app/api/ (via backend-proxy.ts) and the server components
// that fetch java-system/python-engine directly (compound/page.tsx, spaghetti/page.tsx) — one
// place for the fallback URLs, so a future port change can't update one call site and miss another.
export function systemBaseUrl(): string {
  return process.env.SYSTEM_BASE_URL ?? "http://localhost:8080";
}

export function engineBaseUrl(): string {
  return process.env.ENGINE_BASE_URL ?? "http://localhost:8000";
}

// True if any of the given paginated responses left rows out — the panel's "showing only the
// first N" caveat, shared because compound and spaghetti pages both hit this on the same
// GET /api/matches (and compound also on GET /api/users).
export function isTruncated(...pages: { total: number; content: unknown[] }[]): boolean {
  return pages.some((page) => page.total > page.content.length);
}
