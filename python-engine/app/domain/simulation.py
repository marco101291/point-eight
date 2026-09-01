"""The engine itself: `run_simulation` and `run_batch`, straight from section 4 of the doc.

`run_batch` is a plain Python loop, not a NumPy-vectorized batch — the doc's "vectorize the batch"
suggestion is deferred; see DEC-012 in `docs/architecture.md` for why, and what it costs.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Literal

from app.domain.agent import Agent
from app.domain.scenarios import (
    ExternalCrisis,
    MoneyConflict,
    NeedForSpace,
    Routine,
    Scenario,
    TrustBreach,
)
from app.domain.state import RelationshipState

MAX_DAYS = 1000
MIN_INTERVAL_DAYS = 1
MAX_INTERVAL_DAYS = 14

SCENARIOS: tuple[Scenario, ...] = (
    MoneyConflict(),
    TrustBreach(),
    ExternalCrisis(),
    Routine(),
    NeedForSpace(),
)


@dataclass(frozen=True)
class SimulationResult:
    expiry_day: int
    outcome: Literal["collapsed", "survived"]
    final_state: RelationshipState


@dataclass(frozen=True)
class CompatibilityReport:
    compatibility_score: float
    expiry_distribution: list[int]


def random_interval(rng: random.Random) -> int:
    """Days until the next scenario. Uniform for M3 — weighting it by how volatile the agents are
    would be a reasonable next step, but the doc doesn't specify one, so keep it simple."""
    return rng.randint(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)


def pick_next_scenario(
    scenarios: tuple[Scenario, ...], state: RelationshipState, rng: random.Random
) -> Scenario:
    weights = [s.affinity(state) for s in scenarios]
    return rng.choices(scenarios, weights=weights, k=1)[0]


def run_simulation(
    agent_a: Agent, agent_b: Agent, max_days: int = MAX_DAYS, rng: random.Random | None = None
) -> SimulationResult:
    """One relationship, lived out day by day until it collapses or reaches `max_days`.

    Collapse is `state.emotional_state is COLLAPSED` — the Markov chain's absorbing state —
    rather than the section-4 pseudocode's `resentment >= COLLAPSE_THRESHOLD`. Once M3 added a
    discrete state layer (for M5's graph), that state's own terminal condition became the natural
    place to end the simulation instead of a second, separate threshold.
    """
    rng = rng or random.Random()
    state = RelationshipState.initial()
    day = 0

    while day < max_days:
        scenario = pick_next_scenario(SCENARIOS, state, rng)
        outcome = scenario.resolve(state, agent_a, agent_b, rng)
        state = outcome.state

        if state.is_collapsed:
            return SimulationResult(expiry_day=day, outcome="collapsed", final_state=state)

        day += random_interval(rng)

    return SimulationResult(expiry_day=max_days, outcome="survived", final_state=state)


def run_batch(
    agent_a: Agent,
    agent_b: Agent,
    n_simulations: int,
    rng: random.Random | None = None,
) -> CompatibilityReport:
    rng = rng or random.Random()
    results = [run_simulation(agent_a, agent_b, rng=rng) for _ in range(n_simulations)]
    survived = sum(1 for r in results if r.outcome == "survived")
    return CompatibilityReport(
        compatibility_score=survived / n_simulations,
        expiry_distribution=[r.expiry_day for r in results],
    )
