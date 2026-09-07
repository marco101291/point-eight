"use client";

import { useState, type MouseEvent as ReactMouseEvent } from "react";

type Tooltip = { x: number; y: number; label: string };

// Shared by every SVG graph view (Markov graph, spaghetti plot, compound graph): each draws its
// own nodes/edges, but "show a label near the cursor on hover" is identical across all three.
export function useGraphTooltip() {
  const [tooltip, setTooltip] = useState<Tooltip | null>(null);
  const showTooltip = (e: ReactMouseEvent, label: string) =>
    setTooltip({ x: e.clientX, y: e.clientY, label });
  const hideTooltip = () => setTooltip(null);
  return { tooltip, showTooltip, hideTooltip };
}

export function GraphTooltip({ tooltip }: { tooltip: Tooltip | null }) {
  if (!tooltip) return null;
  return (
    <div className="graph-tooltip" style={{ left: tooltip.x + 14, top: tooltip.y + 14 }}>
      {tooltip.label}
    </div>
  );
}
