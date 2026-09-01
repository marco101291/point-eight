"""Relationship state and the Markov chain over emotional states.

`RelationshipState` carries the continuous signals from the section-4 pseudocode (trust,
resentment, satisfaction) plus a discrete `EmotionalState`. The discrete layer isn't in the
original pseudocode — it's what M5's admin panel needs to draw a real "states + transition
probabilities" graph instead of a scalar. `advance_emotional_state` treats the hand-authored
`TRANSITIONS` table as a Markov chain (the next state depends only on the current one) perturbed
by how positive or negative the day's reactions were.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from enum import Enum


class EmotionalState(str, Enum):
    """Discrete emotional climate of the relationship. COLLAPSED is absorbing."""

    STABLE = "stable"
    TENSE = "tense"
    REPAIRING = "repairing"
    HOSTILE = "hostile"
    COLLAPSED = "collapsed"


# How "bad" each state is, used only to decide which direction a perturbation pushes — not part of
# the chain itself. STABLE and REPAIRING sit near each other on purpose: repairing is progress, not
# a setback, but it isn't fully resolved either.
_SEVERITY = {
    EmotionalState.STABLE: 0,
    EmotionalState.REPAIRING: 1,
    EmotionalState.TENSE: 2,
    EmotionalState.HOSTILE: 3,
    EmotionalState.COLLAPSED: 4,
}

# Base transition probabilities, before any day's reactions perturb them. Hand-authored for M3;
# "learned transition probabilities" (per the M5 chart caption) would replace these with values
# fit from real simulation runs, which is out of scope here.
TRANSITIONS: dict[EmotionalState, dict[EmotionalState, float]] = {
    EmotionalState.STABLE: {
        EmotionalState.STABLE: 0.85,
        EmotionalState.TENSE: 0.15,
    },
    EmotionalState.TENSE: {
        EmotionalState.STABLE: 0.25,
        EmotionalState.TENSE: 0.45,
        EmotionalState.REPAIRING: 0.15,
        EmotionalState.HOSTILE: 0.15,
    },
    EmotionalState.REPAIRING: {
        EmotionalState.STABLE: 0.55,
        EmotionalState.TENSE: 0.30,
        EmotionalState.HOSTILE: 0.15,
    },
    EmotionalState.HOSTILE: {
        EmotionalState.TENSE: 0.15,
        EmotionalState.REPAIRING: 0.20,
        EmotionalState.HOSTILE: 0.45,
        EmotionalState.COLLAPSED: 0.20,
    },
    EmotionalState.COLLAPSED: {
        EmotionalState.COLLAPSED: 1.0,
    },
}

# How strongly a day's net positivity/negativity can shift the base transition weights.
_PERTURBATION_STRENGTH = 0.4


@dataclass(frozen=True)
class RelationshipState:
    """Immutable snapshot of a simulated relationship on a given day."""

    trust: float
    resentment: float
    satisfaction: float
    emotional_state: EmotionalState

    @staticmethod
    def initial() -> "RelationshipState":
        """Starting point for every simulation, straight from the section-4 pseudocode."""
        return RelationshipState(
            trust=0.6, resentment=0.0, satisfaction=0.5, emotional_state=EmotionalState.STABLE
        )

    @property
    def is_collapsed(self) -> bool:
        return self.emotional_state is EmotionalState.COLLAPSED


def advance_emotional_state(
    current: EmotionalState, positivity: float, rng: random.Random
) -> EmotionalState:
    """Samples the next emotional state from `current`'s row in `TRANSITIONS`.

    `positivity` is net positive minus net negative reaction charge for the day, roughly in
    [-1, 1]. A positive day nudges probability mass toward states that are less severe than
    `current`; a negative day nudges it toward more severe ones. COLLAPSED is absorbing — callers
    are expected to stop simulating once they reach it, not call this again.
    """
    row = TRANSITIONS[current]
    current_severity = _SEVERITY[current]

    weighted: dict[EmotionalState, float] = {}
    for target, base_weight in row.items():
        direction = current_severity - _SEVERITY[target]  # >0 if target is less severe
        sign = (direction > 0) - (direction < 0)
        factor = 1.0 + positivity * _PERTURBATION_STRENGTH * sign
        weighted[target] = max(base_weight * factor, 0.01)

    total = sum(weighted.values())
    draw = rng.random() * total
    cumulative = 0.0
    for target, weight in weighted.items():
        cumulative += weight
        if draw <= cumulative:
            return target
    return current  # unreachable in practice; guards against float rounding at the boundary
