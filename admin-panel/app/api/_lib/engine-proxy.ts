// Shared by every route under app/api/ that proxies the Engine, so the browser never needs
// ENGINE_BASE_URL directly — that env var is only readable server-side (see next.config.ts).
export async function proxyEngine(path: string): Promise<Response> {
  const engine = process.env.ENGINE_BASE_URL ?? "http://localhost:8000";

  const res = await fetch(`${engine}${path}`, { cache: "no-store" });
  if (!res.ok) {
    const error = res.status === 404 ? "Sin datos para este recurso" : `El Motor respondió ${res.status}`;
    return Response.json({ error }, { status: res.status });
  }

  const body = await res.json();
  return Response.json(body);
}
