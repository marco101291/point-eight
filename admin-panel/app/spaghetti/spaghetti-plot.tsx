"use client";

import { useEffect, useState } from "react";
import { GraphTooltip, useGraphTooltip } from "@/app/_components/graph-tooltip";

export type ScoredMatch = { id: string; compatibilityScore: number };

type TrajectoryPoint = {
  day: number;
  trust: number;
  resentment: number;
  satisfaction: number;
  emotionalState: string;
};

type Trajectory = {
  simulationIndex: number;
  outcome: string;
  expiryDay: number;
  points: TrajectoryPoint[];
};

type MatchTrajectories = {
  matchId: string;
  runId: string;
  compatibilityScore: number;
  trajectories: Trajectory[];
};

const WIDTH = 720;
const HEIGHT = 420;
const MARGIN = { top: 16, right: 16, bottom: 32, left: 40 };
const PLOT_WIDTH = WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = HEIGHT - MARGIN.top - MARGIN.bottom;

export function SpaghettiPlot({ matches }: { matches: ScoredMatch[] }) {
  const [selectedId, setSelectedId] = useState<string | null>(matches[0]?.id ?? null);
  const [data, setData] = useState<MatchTrajectories | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { tooltip, showTooltip, hideTooltip } = useGraphTooltip();

  useEffect(() => {
    if (!selectedId) return;
    // Guards against a stale response from a previously selected match overwriting the data for
    // whichever match is selected by the time the fetch actually resolves.
    let cancelled = false;
    setData(null);
    setError(null);
    fetch(`/api/matches/${selectedId}/trajectories`, { cache: "no-store" })
      .then(async (res) => {
        // Read the body once regardless of status: the proxy's error responses carry a Spanish
        // `error` message (backend-proxy.ts) that's already the right thing to show, rather than
        // a generic status code re-guessed at render time.
        const body = await res.json();
        if (!res.ok) throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`);
        return body as MatchTrajectories;
      })
      .then((json) => {
        if (!cancelled) setData(json);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "sin respuesta");
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  if (matches.length === 0) {
    return (
      <p className="lede">
        Todavía no hay ningún match con score — el plot aparece en cuanto se le pide un score a
        alguno.
      </p>
    );
  }

  const maxDay = data
    ? Math.max(1, ...data.trajectories.flatMap((t) => t.points.map((p) => p.day)))
    : 1;
  const xScale = (day: number) => MARGIN.left + (day / maxDay) * PLOT_WIDTH;
  const yScale = (satisfaction: number) => MARGIN.top + (1 - satisfaction) * PLOT_HEIGHT;

  return (
    <div>
      <ul className="match-picker">
        {matches.map((m) => (
          <li key={m.id}>
            <button
              type="button"
              className={m.id === selectedId ? "selected" : ""}
              onClick={() => setSelectedId(m.id)}
            >
              {m.id.slice(0, 8)} · {(m.compatibilityScore * 100).toFixed(0)}%
            </button>
          </li>
        ))}
      </ul>

      {error && <p className="lede">{error}</p>}
      {!error && !data && <p className="lede">Cargando…</p>}

      {data && (
        <>
          <svg
            viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
            role="img"
            aria-label="Spaghetti plot de satisfacción por día"
            className="spaghetti-plot"
          >
            {[0, 0.5, 1].map((v) => (
              <g key={v}>
                <line
                  x1={MARGIN.left}
                  x2={WIDTH - MARGIN.right}
                  y1={yScale(v)}
                  y2={yScale(v)}
                  stroke="var(--line)"
                />
                <text x={MARGIN.left - 8} y={yScale(v)} textAnchor="end" dominantBaseline="central" fontSize={11} fill="var(--muted)">
                  {v}
                </text>
              </g>
            ))}
            {[0, maxDay / 2, maxDay].map((d) => (
              <text
                key={d}
                x={xScale(d)}
                y={HEIGHT - MARGIN.bottom + 18}
                textAnchor="middle"
                fontSize={11}
                fill="var(--muted)"
              >
                {Math.round(d)}
              </text>
            ))}

            {data.trajectories.map((t) => {
              const points = t.points.map((p) => `${xScale(p.day)},${yScale(p.satisfaction)}`).join(" ");
              const color = t.outcome === "collapsed" ? "var(--down)" : "var(--ok)";
              const label = `Simulación #${t.simulationIndex} — ${t.outcome === "collapsed" ? "colapsó" : "sobrevivió"} en el día ${t.expiryDay}`;
              return (
                <g key={t.simulationIndex}>
                  <polyline points={points} fill="none" stroke={color} strokeWidth={1} opacity={0.35} />
                  {/* Invisible wide stroke purely so a thin line is still easy to hover. */}
                  <polyline
                    points={points}
                    fill="none"
                    stroke="transparent"
                    strokeWidth={8}
                    onMouseMove={(e) => showTooltip(e, label)}
                    onMouseLeave={hideTooltip}
                  />
                </g>
              );
            })}
          </svg>
          <p className="lede">
            Score {(data.compatibilityScore * 100).toFixed(0)}% · {data.trajectories.length}{" "}
            trayectorias muestreadas. Eje X: día de la simulación (hasta {Math.round(maxDay)}). Eje
            Y: satisfacción (0 a 1).
          </p>
        </>
      )}

      <GraphTooltip tooltip={tooltip} />
    </div>
  );
}
