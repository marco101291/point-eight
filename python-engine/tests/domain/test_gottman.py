import math

import pytest

from app.domain.gottman import (
    AT_RISK_RATIO,
    MAX_COLLAPSE_PROBABILITY,
    STABLE_RATIO,
    collapse_probability,
    ratio,
)
from app.domain.state import RelationshipState


def test_ratio_is_infinite_with_no_negative_charge_yet() -> None:
    state = RelationshipState.initial()
    assert ratio(state) == math.inf


def test_ratio_divides_recent_charges() -> None:
    state = RelationshipState(
        trust=0.5,
        resentment=0.1,
        satisfaction=0.5,
        emotional_state=RelationshipState.initial().emotional_state,
        recent_positive=4.0,
        recent_negative=2.0,
    )
    assert ratio(state) == 2.0


def test_collapse_probability_is_zero_at_and_above_the_stable_ratio() -> None:
    assert collapse_probability(STABLE_RATIO) == 0.0
    assert collapse_probability(STABLE_RATIO + 10) == 0.0
    assert collapse_probability(math.inf) == 0.0


def test_collapse_probability_is_maxed_at_and_below_the_at_risk_ratio() -> None:
    assert collapse_probability(AT_RISK_RATIO) == MAX_COLLAPSE_PROBABILITY
    assert collapse_probability(0.0) == MAX_COLLAPSE_PROBABILITY
    assert collapse_probability(-1.0) == MAX_COLLAPSE_PROBABILITY


def test_collapse_probability_interpolates_linearly_between_the_two_ratios() -> None:
    midpoint = (STABLE_RATIO + AT_RISK_RATIO) / 2
    assert collapse_probability(midpoint) == pytest.approx(MAX_COLLAPSE_PROBABILITY / 2)


def test_collapse_probability_decreases_as_ratio_improves() -> None:
    worse = collapse_probability(1.0)
    better = collapse_probability(3.0)
    assert worse > better > 0.0
