import type { Metadata } from "next";
import Link from "next/link";
import { SpaghettiPlot, type ScoredMatch } from "./spaghetti-plot";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "0.8 — Spaghetti Plot",
};

type MatchResponse = {
  id: string;
  userAId: string;
  userBId: string;
  compatibilityScore: number | null;
};

type PageResponse = {
  content: MatchResponse[];
};

type LoadResult = { ok: true; matches: ScoredMatch[] } | { ok: false; error: string };

async function loadScoredMatches(): Promise<LoadResult> {
  const system = process.env.SYSTEM_BASE_URL ?? "http://localhost:8080";
  try {
    const res = await fetch(`${system}/api/matches?size=50`, { cache: "no-store" });
    if (!res.ok) return { ok: false, error: `HTTP ${res.status}` };
    const page = (await res.json()) as PageResponse;
    // Only matches the Engine already scored have a SimulationRun behind them to plot.
    const matches = page.content
      .filter((m): m is MatchResponse & { compatibilityScore: number } => m.compatibilityScore !== null)
      .map((m) => ({ id: m.id, compatibilityScore: m.compatibilityScore }));
    return { ok: true, matches };
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : "sin respuesta" };
  }
}

export default async function SpaghettiPage() {
  const result = await loadScoredMatches();

  return (
    <main>
      <Link href="/" className="back-link">
        ← volver
      </Link>
      <h1>M5 — Spaghetti Plot</h1>
      <p className="lede">
        Las trayectorias día a día de una muestra de 50 simulaciones para un match — satisfacción a
        lo largo del tiempo, verde las que sobreviven, roja las que colapsan.
      </p>
      {result.ok ? (
        <SpaghettiPlot matches={result.matches} />
      ) : (
        <p className="lede">El Sistema no respondió: {result.error}</p>
      )}
    </main>
  );
}
