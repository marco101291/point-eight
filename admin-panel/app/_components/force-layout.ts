import {
  forceCenter,
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  type SimulationLinkDatum,
  type SimulationNodeDatum,
} from "d3-force";

interface LayoutNode extends SimulationNodeDatum {
  id: string;
}

// The four-force scaffold both graph views need (link/charge/center/collide), settled
// synchronously. Shared because the only real difference between the Markov graph's 5 fixed
// states and the compound graph's arbitrary user count is these numbers, not the shape of the
// simulation itself — mutates `nodes` in place, same as calling the d3-force APIs directly would.
export function runForceLayout<N extends LayoutNode, L extends SimulationLinkDatum<N>>(
  nodes: N[],
  links: L[],
  options: {
    linkDistance: (link: L) => number;
    chargeStrength: number;
    center: readonly [number, number];
    collideRadius: number;
    ticks?: number;
  },
): void {
  forceSimulation(nodes)
    .force(
      "link",
      forceLink<N, L>(links)
        .id((d) => d.id)
        .distance(options.linkDistance),
    )
    .force("charge", forceManyBody().strength(options.chargeStrength))
    .force("center", forceCenter(...options.center))
    .force("collide", forceCollide(options.collideRadius))
    .stop()
    .tick(options.ticks ?? 300);
}
