"use client";

import { useEffect, useState } from "react";
import type { SimulationLinkDatum, SimulationNodeDatum } from "d3-force";
import { GraphTooltip, useGraphTooltip } from "@/app/_components/graph-tooltip";
import { runForceLayout } from "@/app/_components/force-layout";

export type CompoundUser = {
  id: string;
  gender: string;
  city: string;
  profession: string;
  cumulativeConfidenceScore: number;
};

export type CompoundMatch = {
  id: string;
  userAId: string;
  userBId: string;
  status: string;
  compatibilityScore: number | null;
};

interface GraphNode extends SimulationNodeDatum {
  id: string;
  user: CompoundUser;
}

interface GraphLink extends SimulationLinkDatum<GraphNode> {
  match: CompoundMatch;
}

const NODE_RADIUS = 7;

function edgeColor(status: string): string {
  if (status === "ACTIVE") return "var(--ok)";
  if (status === "PENDING") return "var(--muted)";
  return "var(--down)"; // EXPIRED, REJECTED
}

type LayoutResult = { nodes: GraphNode[]; viewBox: string };

// Real force layout: nodes repel each other, a link pulls matched users together — clusters are
// whatever the physics settles into, not a hand-picked grouping. Isolated users (no matches) just
// drift to the outskirts under repulsion alone.
//
// The viewBox isn't guessed ahead of time from the node count: with a fixed charge strength, how
// far 20 nodes spread apart isn't the same multiple of how far 4 do (repulsion is pairwise, so it
// grows faster than linearly), so a size formula picked to fit one node count clips another.
// Instead the simulation runs unconstrained around the origin, and the viewBox is fit to whatever
// bounding box it actually settles into.
function layout(users: CompoundUser[], matches: CompoundMatch[]): LayoutResult {
  if (users.length === 0) {
    // Math.min/max(...[]) are Infinity/-Infinity, which would otherwise produce a broken viewBox
    // (page.tsx never renders this component with zero users today, but layout() shouldn't rely
    // on a caller it doesn't control to keep guaranteeing that).
    return { nodes: [], viewBox: "0 0 1 1" };
  }

  const nodes: GraphNode[] = users.map((user, i) => {
    const angle = (2 * Math.PI * i) / Math.max(1, users.length);
    const seedRadius = 20 + 15 * Math.sqrt(users.length);
    return { id: user.id, user, x: seedRadius * Math.cos(angle), y: seedRadius * Math.sin(angle) };
  });

  const links: GraphLink[] = matches.map((m) => ({
    source: m.userAId,
    target: m.userBId,
    match: m,
  }));

  runForceLayout(nodes, links, {
    linkDistance: () => 70,
    chargeStrength: -120,
    center: [0, 0],
    collideRadius: NODE_RADIUS + 6,
  });

  const pad = NODE_RADIUS + 20;
  const xs = nodes.map((n) => n.x ?? 0);
  const ys = nodes.map((n) => n.y ?? 0);
  const minX = Math.min(...xs) - pad;
  const minY = Math.min(...ys) - pad;
  const spanX = Math.max(...xs) - minX + pad;
  const spanY = Math.max(...ys) - minY + pad;

  return { nodes, viewBox: `${minX} ${minY} ${spanX} ${spanY}` };
}

export function CompoundGraph({ users, matches }: { users: CompoundUser[]; matches: CompoundMatch[] }) {
  const [layoutResult, setLayoutResult] = useState<LayoutResult | null>(null);
  const { tooltip, showTooltip, hideTooltip } = useGraphTooltip();

  // The force simulation runs 300 chaotic iterations of Math.cos/sin/sqrt — floating-point results
  // from that can differ by a bit or two between the server's V8 and the browser's, and after 300
  // compounding iterations that's enough to visibly diverge. Computed only after mount, so it
  // never runs during SSR and can't mismatch what hydration re-renders.
  useEffect(() => {
    setLayoutResult(layout(users, matches));
  }, [users, matches]);

  if (!layoutResult) return <p className="lede">Calculando layout…</p>;

  const { nodes, viewBox } = layoutResult;
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const degree = new Map<string, number>();
  for (const m of matches) {
    degree.set(m.userAId, (degree.get(m.userAId) ?? 0) + 1);
    degree.set(m.userBId, (degree.get(m.userBId) ?? 0) + 1);
  }

  return (
    <div>
      <svg
        viewBox={viewBox}
        role="img"
        aria-label="Grafo del compound: usuarios y matches"
        className="compound-graph"
      >
        {matches.map((m) => {
          const from = byId.get(m.userAId);
          const to = byId.get(m.userBId);
          if (!from || !to) return null;
          const label = `${m.status}${
            m.compatibilityScore !== null ? ` · ${(m.compatibilityScore * 100).toFixed(0)}%` : " · sin score"
          }`;
          return (
            <g key={m.id}>
              <line
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                stroke={edgeColor(m.status)}
                strokeWidth={m.status === "ACTIVE" ? 2 : 1}
                opacity={m.status === "ACTIVE" ? 0.8 : 0.4}
              />
              <line
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                stroke="transparent"
                strokeWidth={10}
                onMouseMove={(e) => showTooltip(e, label)}
                onMouseLeave={hideTooltip}
              />
            </g>
          );
        })}

        {nodes.map((n) => (
          <circle
            key={n.id}
            cx={n.x}
            cy={n.y}
            r={NODE_RADIUS}
            fill="var(--bg)"
            stroke="var(--fg)"
            strokeWidth={1.5}
            onMouseMove={(e) =>
              showTooltip(
                e,
                `${n.user.gender} · ${n.user.city} · ${n.user.profession} — ${
                  degree.get(n.id) ?? 0
                } match(es), confianza ${(n.user.cumulativeConfidenceScore * 100).toFixed(0)}%`,
              )
            }
            onMouseLeave={hideTooltip}
          />
        ))}
      </svg>
      <p className="lede">
        {users.length} usuario(s), {matches.length} match(es). Verde = ACTIVE, gris = PENDING, rojo
        = EXPIRED/REJECTED. Pasá el mouse sobre un nodo o una línea para el detalle.
      </p>
      <GraphTooltip tooltip={tooltip} />
    </div>
  );
}
