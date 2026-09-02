"""The math the project is named after.

Gottman/Levenson's finding: stable couples sustain a 5:1 positive:negative ratio during conflict;
at ~0.8:1 (negatives nearly matching positives), it's a strong breakup signal. `collapse_probability`
interpolates between those two real numbers instead of using a binary cutoff — deliberately kept
separate from the discrete `EmotionalState` Markov chain in `state.py` (DEC-011): the ratio predicts
breakup independent of the couple's momentary emotional climate, so a pair stuck in TENSE without
ever reaching HOSTILE can still be worn down by a bad ratio over time.
"""

from __future__ import annotations

import math

from app.domain.state import RelationshipState

STABLE_RATIO = 5.0
AT_RISK_RATIO = 0.8

# The odds of collapsing on any single scenario once the ratio is at or below AT_RISK_RATIO. Not
# from the research (Gottman's lab predicted divorce over years of observation, not a daily hazard
# rate) — calibrated empirically instead: a full simulation checks this ~130 times (1000 max days
# / ~7.5-day average interval), so even a small per-scenario probability compounds hard. At 0.15,
# every pair collapsed almost regardless of compatibility; 0.005 gives a secure, low-conflict pair
# roughly 70% survival and a volatile, high-conflict pair roughly 30% — a real gradient instead of
# everyone converging to 0.
MAX_COLLAPSE_PROBABILITY = 0.005


def ratio(state: RelationshipState) -> float:
    """Recent positive:negative ratio (an EMA, not a lifetime total — see RelationshipState's
    docstring). No negative charge yet reads as maximally safe (infinity), not zero — there's
    nothing to be at risk from yet."""
    if state.recent_negative == 0.0:
        return math.inf
    return state.recent_positive / state.recent_negative


def collapse_probability(current_ratio: float) -> float:
    """0 at STABLE_RATIO and above, MAX_COLLAPSE_PROBABILITY at AT_RISK_RATIO and below, linear
    in between."""
    if current_ratio >= STABLE_RATIO:
        return 0.0
    if current_ratio <= AT_RISK_RATIO:
        return MAX_COLLAPSE_PROBABILITY

    span = STABLE_RATIO - AT_RISK_RATIO
    closeness_to_risk = (STABLE_RATIO - current_ratio) / span
    return MAX_COLLAPSE_PROBABILITY * closeness_to_risk
