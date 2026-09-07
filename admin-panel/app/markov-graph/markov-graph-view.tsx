"use client";

import { useEffect, useState } from "react";
import type { SimulationLinkDatum, SimulationNodeDatum } from "d3-force";
import { GraphTooltip, useGraphTooltip } from "@/app/_components/graph-tooltip";
import { runForceLayout } from "@/app/_components/force-layout";

type MarkovTransition = {
  fromState: string;
  toState: string;
  count: number;
  probability: number;
};

type MarkovGraphData = {
  states: string[];
  transitions: MarkovTransition[];
  totalObservations: number;
};

interface GraphNode extends SimulationNodeDatum {
  id: string;
}

interface GraphLink extends SimulationLinkDatum<GraphNode> {
  probability: number;
}

const WIDTH = 720;
const HEIGHT = 520;
const NODE_RADIUS = 32;

// Lays the states out with a real force simulation — repulsion between every pair, plus a link
// force whose distance shrinks as the transition it represents gets stronger — instead of a
// hand-picked layout. Self-loops are excluded from the link force (a link whose source and target
// are the same node has no length to converge to) and drawn separately, once positions settle.
function layout(states: string[], crossLinks: MarkovTransition[]): GraphNode[] {
  const nodes: GraphNode[] = states.map((id, i) => {
    const angle = (2 * Math.PI * i) / states.length;
    return {
      id,
      x: WIDTH / 2 + 140 * Math.cos(angle),
      y: HEIGHT / 2 + 140 * Math.sin(angle),
    };
  });

  const links: GraphLink[] = crossLinks.map((t) => ({
    source: t.fromState,
    target: t.toState,
    probability: t.probability,
  }));

  // Run to completion synchronously: 5 static states settle in well under 300 ticks, and there's
  // no reason to animate a graph that never changes after this render.
  runForceLayout(nodes, links, {
    linkDistance: (d) => 220 - 160 * d.probability,
    chargeStrength: -450,
    center: [WIDTH / 2, HEIGHT / 2],
    collideRadius: NODE_RADIUS + 12,
  });

  return nodes;
}

function edgePath(from: GraphNode, to: GraphNode, bow: number) {
  const [x1, y1] = [from.x ?? 0, from.y ?? 0];
  const [x2, y2] = [to.x ?? 0, to.y ?? 0];
  const mx = (x1 + x2) / 2;
  const my = (y1 + y2) / 2;
  // Perpendicular offset so a pair of opposite-direction edges (stable -> tense and
  // tense -> stable) doesn't draw the two arrows on top of each other.
  const dx = x2 - x1;
  const dy = y2 - y1;
  const len = Math.hypot(dx, dy) || 1;
  const cx = mx + (-dy / len) * bow;
  const cy = my + (dx / len) * bow;
  return { d: `M ${x1} ${y1} Q ${cx} ${cy} ${x2} ${y2}`, mid: [cx, cy] as [number, number] };
}

export function MarkovGraphView() {
  const [data, setData] = useState<MarkovGraphData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { tooltip, showTooltip, hideTooltip } = useGraphTooltip();

  useEffect(() => {
    fetch("/api/markov-graph", { cache: "no-store" })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<MarkovGraphData>;
      })
      .then(setData)
      .catch((err) => setError(err instanceof Error ? err.message : "sin respuesta"));
  }, []);

  if (error) return <p className="lede">El Motor no respondió: {error}</p>;
  if (!data) return <p className="lede">Cargando…</p>;
  if (data.totalObservations === 0) {
    return (
      <p className="lede">
        Todavía no se registró ninguna transición — el grafo se completa a medida que se piden
        scores de compatibilidad.
      </p>
    );
  }

  const selfLoops = new Map(
    data.transitions
      .filter((t) => t.fromState === t.toState)
      .map((t) => [t.fromState, { probability: t.probability, count: t.count }] as const),
  );
  const crossLinks = data.transitions.filter((t) => t.fromState !== t.toState);
  const nodes = layout(data.states, crossLinks);
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const seenPairs = new Set<string>();

  return (
    <div>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label="Grafo de Markov de estados emocionales"
        className="markov-graph"
      >
        <defs>
          <marker
            id="arrow"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--muted)" />
          </marker>
        </defs>

        {crossLinks.map((t) => {
          const from = byId.get(t.fromState);
          const to = byId.get(t.toState);
          if (!from || !to) return null;
          // The reverse edge, if it exists, bows the other way so both arrows are visible.
          const pairKey = [t.fromState, t.toState].sort().join("|");
          const bow = seenPairs.has(pairKey) ? -36 : 36;
          seenPairs.add(pairKey);
          const { d, mid } = edgePath(from, to, bow);
          const label = `${t.fromState} → ${t.toState}: ${(t.probability * 100).toFixed(1)}% (${t.count.toLocaleString("es")} observaciones)`;
          return (
            <g key={`${t.fromState}-${t.toState}`}>
              <path
                d={d}
                fill="none"
                stroke="var(--muted)"
                strokeWidth={1 + t.probability * 5}
                opacity={0.35 + t.probability * 0.5}
                markerEnd="url(#arrow)"
              />
              {/* Invisible wide stroke purely so thin, low-probability edges are still easy to
                  hover — the visible path above stays thin regardless of this hit area. */}
              <path
                d={d}
                fill="none"
                stroke="transparent"
                strokeWidth={16}
                onMouseMove={(e) => showTooltip(e, label)}
                onMouseLeave={hideTooltip}
              />
              <text
                x={mid[0]}
                y={mid[1]}
                textAnchor="middle"
                fontSize={12}
                fill="var(--fg)"
                stroke="var(--bg)"
                strokeWidth={4}
                paintOrder="stroke"
              >
                {(t.probability * 100).toFixed(0)}%
              </text>
            </g>
          );
        })}

        {nodes.map((n) => (
          <g key={n.id} transform={`translate(${n.x ?? 0}, ${n.y ?? 0})`}>
            <circle r={NODE_RADIUS} fill="var(--bg)" stroke="var(--fg)" strokeWidth={1.5} />
            <text
              textAnchor="middle"
              dominantBaseline="central"
              fontSize={11}
              letterSpacing="0.5"
              fill="var(--fg)"
            >
              {n.id}
            </text>
            {selfLoops.has(n.id) && (
              <text
                y={-NODE_RADIUS - 10}
                textAnchor="middle"
                fontSize={11}
                fill="var(--ok)"
                onMouseMove={(e) =>
                  showTooltip(
                    e,
                    `${n.id} → ${n.id}: ${(selfLoops.get(n.id)!.probability * 100).toFixed(1)}% (${selfLoops
                      .get(n.id)!
                      .count.toLocaleString("es")} observaciones)`,
                  )
                }
                onMouseLeave={hideTooltip}
              >
                ↻ {(selfLoops.get(n.id)!.probability * 100).toFixed(0)}%
              </text>
            )}
          </g>
        ))}
      </svg>
      <p className="lede">
        {data.totalObservations.toLocaleString("es")} transiciones observadas en total, sumadas de
        cada score pedido. El grosor y la opacidad de cada arista son proporcionales a su
        probabilidad; ↻ es la probabilidad de quedarse en el mismo estado. Pasá el mouse sobre una
        arista para ver el conteo exacto.
      </p>
      <GraphTooltip tooltip={tooltip} />
    </div>
  );
}
