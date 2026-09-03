import type { Metadata } from "next";
import Link from "next/link";
import { CompoundGraph, type CompoundUser, type CompoundMatch } from "./compound-graph";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "0.8 — El Compound",
};

type UserResponse = {
  id: string;
  gender: string;
  city: string;
  profession: string;
  cumulativeConfidenceScore: number;
};

type MatchResponse = {
  id: string;
  userAId: string;
  userBId: string;
  status: string;
  compatibilityScore: number | null;
};

type PageResponse<T> = { content: T[]; total: number };

type LoadResult =
  | { ok: true; users: CompoundUser[]; matches: CompoundMatch[]; truncated: boolean }
  | { ok: false; error: string };

const FETCH_SIZE = 500;

async function loadCompound(): Promise<LoadResult> {
  const system = process.env.SYSTEM_BASE_URL ?? "http://localhost:8080";
  try {
    const [usersRes, matchesRes] = await Promise.all([
      fetch(`${system}/api/users?size=${FETCH_SIZE}`, { cache: "no-store" }),
      fetch(`${system}/api/matches?size=${FETCH_SIZE}`, { cache: "no-store" }),
    ]);
    if (!usersRes.ok) return { ok: false, error: `usuarios: HTTP ${usersRes.status}` };
    if (!matchesRes.ok) return { ok: false, error: `matches: HTTP ${matchesRes.status}` };

    const usersPage = (await usersRes.json()) as PageResponse<UserResponse>;
    const matchesPage = (await matchesRes.json()) as PageResponse<MatchResponse>;

    const users: CompoundUser[] = usersPage.content.map((u) => ({
      id: u.id,
      gender: u.gender,
      city: u.city,
      profession: u.profession,
      cumulativeConfidenceScore: u.cumulativeConfidenceScore,
    }));
    const userIds = new Set(users.map((u) => u.id));
    // Defensive: a match referencing a user outside this page (only possible past FETCH_SIZE
    // users) would otherwise render as a dangling edge with no node to connect to.
    const matches: CompoundMatch[] = matchesPage.content
      .filter((m) => userIds.has(m.userAId) && userIds.has(m.userBId))
      .map((m) => ({
        id: m.id,
        userAId: m.userAId,
        userBId: m.userBId,
        status: m.status,
        compatibilityScore: m.compatibilityScore,
      }));

    const truncated = usersPage.total > users.length || matchesPage.total > matchesPage.content.length;
    return { ok: true, users, matches, truncated };
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : "sin respuesta" };
  }
}

export default async function CompoundPage() {
  const result = await loadCompound();

  return (
    <main>
      <Link href="/" className="back-link">
        ← volver
      </Link>
      <h1>M5 — El Compound</h1>
      <p className="lede">
        Todos los usuarios y matches del sistema en un solo grafo — cada línea es un match (en
        cualquier estado), y los clusters emergen del layout de fuerzas, no de una regla escrita a
        mano.
      </p>
      {!result.ok && <p className="lede">El Sistema no respondió: {result.error}</p>}
      {result.ok && result.users.length === 0 && (
        <p className="lede">Todavía no hay usuarios registrados.</p>
      )}
      {result.ok && result.users.length > 0 && (
        <>
          {result.truncated && (
            <p className="lede">
              Mostrando los primeros {FETCH_SIZE} usuarios/matches — hay más de los que se ven acá.
            </p>
          )}
          <CompoundGraph users={result.users} matches={result.matches} />
        </>
      )}
    </main>
  );
}
