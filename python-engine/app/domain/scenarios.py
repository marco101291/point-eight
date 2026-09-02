"""Conflict scenarios as Strategy, per section 2 of the doc.

Each `Scenario` owns its own resolution end-to-end (`resolve`), rather than the main loop calling
`agent.react()` and a free `update_state()` separately like the section-4 pseudocode does — see
DEC-011 in `docs/architecture.md` for why. Internally each scenario still does exactly that; the
difference is that it's no longer the caller's job to know how.
"""

from __future__ import annotations

import random
from abc import ABC
from dataclasses import dataclass

from app.domain.agent import Agent
from app.domain.reaction import Reaction
from app.domain.state import RelationshipState, advance_emotional_state


@dataclass(frozen=True)
class ScenarioOutcome:
    """What resolving a scenario produces: the new state, and both reactions for anyone who
    wants to log or graph them (M5's spaghetti plot, eventually)."""

    state: RelationshipState
    reaction_a: Reaction
    reaction_b: Reaction


class Scenario(ABC):
    """A day's conflict (or non-conflict) event. `base_negative`/`base_positive` are the
    scenario's intrinsic charge before any agent's personality reshapes it."""

    base_negative: float = 0.0
    base_positive: float = 0.0

    # Duck-typed hooks `Agent.react()` checks instead of isinstance-ing concrete scenarios.
    wants_space: bool = False
    is_trust_breach: bool = False

    # Relative likelihood of this scenario occurring, indexed by the relationship's current
    # emotional state; states not listed default to 1.0. Hand-tuned, not learned.
    affinity_by_state: dict[str, float] = {}

    def affinity(self, state: RelationshipState) -> float:
        return self.affinity_by_state.get(state.emotional_state, 1.0)

    def resolve(
        self, state: RelationshipState, agent_a: Agent, agent_b: Agent, rng: random.Random
    ) -> ScenarioOutcome:
        reaction_a = agent_a.react(self, state, rng)
        reaction_b = agent_b.react(self, state, rng)
        new_state = update_state(state, reaction_a, reaction_b, rng)
        return ScenarioOutcome(state=new_state, reaction_a=reaction_a, reaction_b=reaction_b)


# Both conflict scenarios below get a low `stable`/`repairing` affinity, not just a high `tense`/
# `hostile` one: a real healthy couple doesn't hit a trust crisis or a money fight every other
# week. Without this, MoneyConflict/TrustBreach occurred often enough even for a low-negativity
# pair that no simulation could sustain a good enough Gottman ratio (`app/domain/gottman.py`) to
# ever approach STABLE_RATIO — every pair converged toward the same mediocre survival odds
# regardless of how compatible the agents actually were, which defeats the point of the score.
class MoneyConflict(Scenario):
    base_negative = 0.55
    base_positive = 0.15
    affinity_by_state = {"tense": 1.4, "hostile": 1.3, "stable": 0.2, "repairing": 0.3}


class TrustBreach(Scenario):
    base_negative = 0.80
    base_positive = 0.05
    is_trust_breach = True
    affinity_by_state = {"tense": 1.2, "hostile": 1.5, "stable": 0.1, "repairing": 0.2}


class ExternalCrisis(Scenario):
    """Doesn't originate inside the relationship, so it doesn't care about the current climate."""

    base_negative = 0.35
    base_positive = 0.35


class Routine(Scenario):
    base_negative = 0.10
    base_positive = 0.40
    affinity_by_state = {"stable": 1.3, "repairing": 1.2, "hostile": 0.5}


class NeedForSpace(Scenario):
    base_negative = 0.30
    base_positive = 0.20
    wants_space = True
    affinity_by_state = {"tense": 1.3}


# Exponential-moving-average decay for recent_positive/recent_negative (see RelationshipState's
# docstring for why it's an EMA, not a running total). 0.85 gives an effective memory of about
# 1 / (1 - 0.85) ≈ 6-7 scenarios — long enough to smooth out one bad day, short enough to actually
# recover from a rough patch instead of dragging the ratio down forever.
_RATIO_MEMORY = 0.85


def update_state(
    state: RelationshipState, reaction_a: Reaction, reaction_b: Reaction, rng: random.Random
) -> RelationshipState:
    """Nudges trust/resentment/satisfaction from one day's reactions and advances the discrete
    Markov layer. Kept as a free function (not a method on `RelationshipState`) since it needs
    both reactions, not just the state being transitioned."""
    net_positive = (reaction_a.positive + reaction_b.positive) / 2.0
    net_negative = (reaction_a.negative + reaction_b.negative) / 2.0

    trust = _clamp01(state.trust + 0.08 * net_positive - 0.12 * net_negative)
    resentment = _clamp01(state.resentment + 0.15 * net_negative - 0.05 * net_positive)
    satisfaction = _clamp01(state.satisfaction + 0.10 * net_positive - 0.08 * net_negative)

    positivity = net_positive - net_negative
    emotional_state = advance_emotional_state(state.emotional_state, positivity, rng)

    recent_positive = _RATIO_MEMORY * state.recent_positive + (1 - _RATIO_MEMORY) * net_positive
    recent_negative = _RATIO_MEMORY * state.recent_negative + (1 - _RATIO_MEMORY) * net_negative

    return RelationshipState(
        trust=trust,
        resentment=resentment,
        satisfaction=satisfaction,
        emotional_state=emotional_state,
        recent_positive=recent_positive,
        recent_negative=recent_negative,
    )


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))
