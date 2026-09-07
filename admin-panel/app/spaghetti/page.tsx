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
  total: number;
};

type LoadResult =
  | { ok: true; matches: ScoredMatch[]; truncated: boolean }
  | { ok: false; error: string };

const FETCH_SIZE = 50;

async function loadScoredMatches(): Promise<LoadResult> {
  const system = process.env.SYSTEM_BASE_URL ?? "http://localhost:8080";
  try {
    const res = await fetch(`${system}/api/matches?size=${FETCH_SIZE}`, { cache: "no-store" });
    if (!res.ok) return { ok: false, error: `HTTP ${res.status}` };
    const page = (await res.json()) as PageResponse;
    // Only matches the Engine already scored have a SimulationRun behind them to plot.
    const matches = page.content
      .filter((m): m is MatchResponse & { compatibilityScore: number } => m.compatibilityScore !== null)
      .map((m) => ({ id: m.id, compatibilityScore: m.compatibilityScore }));
    // Past FETCH_SIZE total matches, older scored ones can silently fall off this page — same
    // caveat compound/page.tsx surfaces for the identical limitation on the same endpoint.
    const truncated = page.total > page.content.length;
    return { ok: true, matches, truncated };
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
      {result.ok && result.truncated && (
        <p className="lede">
          Mostrando los primeros {FETCH_SIZE} matches — puede haber matches con score más viejos
          que no se ven acá.
        </p>
      )}
      {result.ok ? (
        <SpaghettiPlot matches={result.matches} />
      ) : (
        <p className="lede">El Sistema no respondió: {result.error}</p>
      )}
    </main>
  );
}
