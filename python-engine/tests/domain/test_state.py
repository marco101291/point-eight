import random

from app.domain.state import EmotionalState, RelationshipState, TRANSITIONS, advance_emotional_state


def test_initial_state_matches_pseudocode() -> None:
    state = RelationshipState.initial()
    assert state.trust == 0.6
    assert state.resentment == 0.0
    assert state.satisfaction == 0.5
    assert state.emotional_state is EmotionalState.STABLE
    assert not state.is_collapsed


def test_collapsed_is_absorbing() -> None:
    rng = random.Random(1)
    for _ in range(50):
        next_state = advance_emotional_state(EmotionalState.COLLAPSED, positivity=1.0, rng=rng)
        assert next_state is EmotionalState.COLLAPSED


def test_every_state_reaches_collapsed_eventually() -> None:
    """Every row must have a path to COLLAPSED, or run_simulation could loop past max_days
    forever waiting for a collapse that structurally can't happen."""
    reachable = {EmotionalState.COLLAPSED}
    changed = True
    while changed:
        changed = False
        for source, row in TRANSITIONS.items():
            if source in reachable:
                continue
            if any(target in reachable for target in row):
                reachable.add(source)
                changed = True
    assert reachable == set(EmotionalState)


def test_repairing_is_reachable() -> None:
    sources_reaching_repairing = [
        source for source, row in TRANSITIONS.items() if EmotionalState.REPAIRING in row
    ]
    assert sources_reaching_repairing, "REPAIRING must be reachable from at least one state"


def test_positive_days_bias_toward_less_severe_states() -> None:
    """Statistical, not exact: over many trials from TENSE, a strongly positive day should land
    on STABLE/REPAIRING more often than a strongly negative day does."""
    rng = random.Random(42)
    trials = 2000

    def stable_or_repairing_rate(positivity: float) -> float:
        hits = sum(
            1
            for _ in range(trials)
            if advance_emotional_state(EmotionalState.TENSE, positivity, rng)
            in (EmotionalState.STABLE, EmotionalState.REPAIRING)
        )
        return hits / trials

    positive_rate = stable_or_repairing_rate(0.8)
    negative_rate = stable_or_repairing_rate(-0.8)
    assert positive_rate > negative_rate


def test_transition_rows_sum_to_one() -> None:
    for state, row in TRANSITIONS.items():
        assert abs(sum(row.values()) - 1.0) < 1e-9, f"{state} row doesn't sum to 1.0"
