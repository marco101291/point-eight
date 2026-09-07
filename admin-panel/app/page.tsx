import Link from "next/link";
import { Logo } from "./logo";

export const dynamic = "force-dynamic";

type Probe = { name: string; url: string; detail: string; up: boolean };

async function probe(name: string, url: string): Promise<Probe> {
  try {
    const res = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(3000) });
    if (!res.ok) return { name, url, detail: `HTTP ${res.status}`, up: false };
    const body = (await res.json()) as { message?: string; milestone?: string };
    return {
      name,
      url,
      detail: [body.milestone, body.message].filter(Boolean).join(" · ") || "ok",
      up: true,
    };
  } catch (err) {
    return { name, url, detail: err instanceof Error ? err.message : "sin respuesta", up: false };
  }
}

export default async function Home() {
  const system = process.env.SYSTEM_BASE_URL ?? "http://localhost:8080";
  const engine = process.env.ENGINE_BASE_URL ?? "http://localhost:8000";

  const services = await Promise.all([
    probe("El Sistema · Java", `${system}/api/status`),
    probe("El Motor · Python", `${engine}/api/status`),
  ]);

  return (
    <main>
      <div className="logo-wrap">
        <Logo />
      </div>
      <h1>M0 — Setup</h1>
      <p className="lede">
        Monorepo levantado. Tres servicios, dos bases de datos y una cola. El dominio real empieza en
        M1; por ahora esto sólo confirma que el compound responde.
      </p>
      <ul className="services">
        {services.map((s) => (
          <li className="service" key={s.name}>
            <div>
              <div className="name">{s.name}</div>
              <div className="detail">{s.detail}</div>
            </div>
            <span className={`badge ${s.up ? "up" : "down"}`}>{s.up ? "en línea" : "caído"}</span>
          </li>
        ))}
      </ul>
      <p className="nav">
        <Link href="/markov-graph">Grafo de Markov →</Link>
      </p>
      <p className="nav">
        <Link href="/spaghetti">Spaghetti Plot →</Link>
      </p>
      <p className="nav">
        <Link href="/compound">El Compound →</Link>
      </p>
      <p className="nav">
        <Link href="/live">El Sistema Decidiendo →</Link>
      </p>
    </main>
  );
}
