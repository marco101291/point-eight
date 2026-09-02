"""The engine itself: `run_simulation` and `run_batch`, straight from section 4 of the doc.

`run_batch` is a plain Python loop, not a NumPy-vectorized batch — the doc's "vectorize the batch"
suggestion is deferred; see DEC-012 in `docs/architecture.md` for why, and what it costs.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Literal, Protocol

from app.domain.agent import Agent
from app.domain.gottman import collapse_probability, ratio
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


class SimulationObserver(Protocol):
    """Optional collaborator: `run_simulation`/`run_batch` narrate what happened to it, if given
    one, but never depend on what it does with that — keeps this module free of persistence
    concerns (M5's `SamplingRecorder`, in `app/services/simulation_recording.py`, is the only
    implementation so far)."""

    def on_day(self, simulation_index: int, day: int, state: RelationshipState) -> None: ...

    def on_simulation_end(self, simulation_index: int, result: SimulationResult) -> None: ...


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
    agent_a: Agent,
    agent_b: Agent,
    max_days: int = MAX_DAYS,
    rng: random.Random | None = None,
    observer: SimulationObserver | None = None,
    simulation_index: int = 0,
) -> SimulationResult:
    """One relationship, lived out day by day until it collapses or reaches `max_days`.

    Two independent ways to collapse, both checked every scenario: reaching `EmotionalState.
    COLLAPSED` (the M3 Markov chain's absorbing state, tracking momentary emotional climate), or
    a bad Gottman ratio simply wearing the relationship down (`collapse_probability`, M4) — a pair
    that never reaches HOSTILE but sustains a poor ratio can still end this way, matching Gottman's
    finding that the ratio predicts breakup independent of any single conflict's intensity.

    `observer`/`simulation_index` are M5's addition, for `run_batch` to report each day's state
    and each simulation's outcome to whoever's collecting it — this function doesn't know or care
    who that is, or whether anyone's listening at all.
    """
    rng = rng or random.Random()
    state = RelationshipState.initial()
    day = 0

    while day < max_days:
        scenario = pick_next_scenario(SCENARIOS, state, rng)
        outcome = scenario.resolve(state, agent_a, agent_b, rng)
        state = outcome.state

        if observer is not None:
            observer.on_day(simulation_index, day, state)

        ratio_collapse = rng.random() < collapse_probability(ratio(state))
        if state.is_collapsed or ratio_collapse:
            result = SimulationResult(expiry_day=day, outcome="collapsed", final_state=state)
            if observer is not None:
                observer.on_simulation_end(simulation_index, result)
            return result

        day += random_interval(rng)

    result = SimulationResult(expiry_day=max_days, outcome="survived", final_state=state)
    if observer is not None:
        observer.on_simulation_end(simulation_index, result)
    return result


def run_batch(
    agent_a: Agent,
    agent_b: Agent,
    n_simulations: int,
    rng: random.Random | None = None,
    observer: SimulationObserver | None = None,
) -> CompatibilityReport:
    rng = rng or random.Random()
    results = [
        run_simulation(agent_a, agent_b, rng=rng, observer=observer, simulation_index=i)
        for i in range(n_simulations)
    ]
    survived = sum(1 for r in results if r.outcome == "survived")
    return CompatibilityReport(
        compatibility_score=survived / n_simulations,
        expiry_distribution=[r.expiry_day for r in results],
    )
