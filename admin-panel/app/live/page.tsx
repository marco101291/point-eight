import type { Metadata } from "next";
import Link from "next/link";
import { LiveFeed } from "./live-feed";

export const metadata: Metadata = {
  title: "0.8 — El Sistema Decidiendo",
};

export default function LivePage() {
  return (
    <main>
      <Link href="/" className="back-link">
        ← volver
      </Link>
      <h1>M5 — El Sistema Decidiendo</h1>
      <p className="lede">
        Cada vez que el Sistema asigna un match o uno expira, sin que nadie lo pida — incluida la
        reasignación automática cuando un match expirado libera a los dos usuarios.
      </p>
      <LiveFeed />
    </main>
  );
}
