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
#
# STABLE's self-loop is deliberately close to 1 (M4 recalibration): a full simulation runs roughly
# 130 scenarios (1000 max days / ~7.5-day average interval), and `0.85 ** 130 ≈ 0` — the original
# M3 weight made it essentially impossible for *any* pair, however compatible, to stay STABLE for
# a whole simulation. At 0.97, a genuinely good pair (whose positive days push the self-loop even
# higher — see `advance_emotional_state`) can actually sustain it.
TRANSITIONS: dict[EmotionalState, dict[EmotionalState, float]] = {
    EmotionalState.STABLE: {
        EmotionalState.STABLE: 0.97,
        EmotionalState.TENSE: 0.03,
    },
    EmotionalState.TENSE: {
        EmotionalState.STABLE: 0.35,
        EmotionalState.TENSE: 0.40,
        EmotionalState.REPAIRING: 0.15,
        EmotionalState.HOSTILE: 0.10,
    },
    EmotionalState.REPAIRING: {
        EmotionalState.STABLE: 0.70,
        EmotionalState.TENSE: 0.25,
        EmotionalState.HOSTILE: 0.05,
    },
    EmotionalState.HOSTILE: {
        EmotionalState.TENSE: 0.20,
        EmotionalState.REPAIRING: 0.25,
        EmotionalState.HOSTILE: 0.45,
        EmotionalState.COLLAPSED: 0.10,
    },
    EmotionalState.COLLAPSED: {
        EmotionalState.COLLAPSED: 1.0,
    },
}

# How strongly a day's net positivity/negativity can shift the base transition weights.
_PERTURBATION_STRENGTH = 0.4


@dataclass(frozen=True)
class RelationshipState:
    """Immutable snapshot of a simulated relationship on a given day.

    `recent_positive`/`recent_negative` are exponential moving averages, not lifetime totals — the
    Gottman-ratio inputs (see `app/domain/gottman.py`). A true running total was tried first and
    rejected: a single early `TrustBreach` would drag the ratio down for the rest of a
    thousand-day simulation even if every scenario afterward was healthy, which made
    `collapse_probability` eventually collapse nearly everyone regardless of how good the pair
    actually was. An EMA lets the ratio reflect recent climate and recover after a rough patch,
    closer to what a 15-minute Gottman lab observation actually samples.
    """

    trust: float
    resentment: float
    satisfaction: float
    emotional_state: EmotionalState
    recent_positive: float = 0.0
    recent_negative: float = 0.0

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

    The self-loop (staying in `current`) isn't neutral to `positivity` either, but which direction
    it moves depends on whether `current` is already the best state: from STABLE, a positive day
    should reinforce staying there; from anywhere worse, a positive day should encourage actually
    moving on rather than settling into "improved, but still stuck".
    """
    row = TRANSITIONS[current]
    current_severity = _SEVERITY[current]

    weighted: dict[EmotionalState, float] = {}
    for target, base_weight in row.items():
        direction = current_severity - _SEVERITY[target]  # >0 if target is less severe
        if direction == 0:  # the self-loop
            sign = 1 if current is EmotionalState.STABLE else -1
        else:
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
