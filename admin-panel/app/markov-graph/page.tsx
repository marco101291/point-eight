import type { Metadata } from "next";
import Link from "next/link";
import { MarkovGraphView } from "./markov-graph-view";

export const metadata: Metadata = {
  title: "0.8 — Grafo de Markov",
};

export default function MarkovGraphPage() {
  return (
    <main>
      <Link href="/" className="back-link">
        ← volver
      </Link>
      <h1>M5 — Grafo de Markov</h1>
      <p className="lede">
        Los cinco estados emocionales discretos y las probabilidades de transición aprendidas de
        cada simulación corrida hasta ahora, no la tabla `TRANSITIONS` escrita a mano en
        `app/domain/state.py`.
      </p>
      <MarkovGraphView />
    </main>
  );
}
