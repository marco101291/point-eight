// Shared by every route under app/api/ that proxies one of the two backend services, so the
// browser never needs their base URLs directly — those env vars are only readable server-side
// (see next.config.ts).
async function proxy(baseUrl: string, path: string, label: string): Promise<Response> {
  const res = await fetch(`${baseUrl}${path}`, { cache: "no-store" });
  if (!res.ok) {
    const error = res.status === 404 ? "Sin datos para este recurso" : `${label} respondió ${res.status}`;
    return Response.json({ error }, { status: res.status });
  }

  const body = await res.json();
  return Response.json(body);
}

export function proxyEngine(path: string): Promise<Response> {
  const engine = process.env.ENGINE_BASE_URL ?? "http://localhost:8000";
  return proxy(engine, path, "El Motor");
}

export function proxySystem(path: string): Promise<Response> {
  const system = process.env.SYSTEM_BASE_URL ?? "http://localhost:8080";
  return proxy(system, path, "El Sistema");
}
