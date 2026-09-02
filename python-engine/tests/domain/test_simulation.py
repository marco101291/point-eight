import random

import pytest

from app.domain import simulation as simulation_module
from app.domain.agent import Agent, AttachmentStyle, CommunicationProfile
from app.domain.simulation import MAX_DAYS, run_batch, run_simulation


def volatile_agent() -> Agent:
    return Agent(
        attachment_style=AttachmentStyle.DISORGANIZED,
        attachment_intensity=0.9,
        communication_profile=CommunicationProfile(0.8, 0.8, 0.8, 0.8),
        infidelity_history=True,
        relationship_history=0,
        active_addiction=True,
        stress_baseline=0.9,
        commitment_pace_expectation=0.5,
    )


def stable_agent() -> Agent:
    return Agent(
        attachment_style=AttachmentStyle.SECURE,
        attachment_intensity=0.2,
        communication_profile=CommunicationProfile(0.05, 0.05, 0.05, 0.05),
        infidelity_history=False,
        relationship_history=2,
        active_addiction=False,
        stress_baseline=0.2,
        commitment_pace_expectation=0.5,
    )


def test_run_simulation_terminates_within_max_days() -> None:
    rng = random.Random(1)
    result = run_simulation(volatile_agent(), volatile_agent(), rng=rng)
    assert 0 <= result.expiry_day <= MAX_DAYS
    assert result.outcome in ("collapsed", "survived")
    if result.outcome == "survived":
        assert result.expiry_day == MAX_DAYS


def test_volatile_pair_collapses_more_often_than_stable_pair() -> None:
    rng = random.Random(2)
    n = 200
    volatile_report = run_batch(volatile_agent(), volatile_agent(), n_simulations=n, rng=rng)
    stable_report = run_batch(stable_agent(), stable_agent(), n_simulations=n, rng=rng)

    assert stable_report.compatibility_score > volatile_report.compatibility_score


def test_run_batch_returns_one_expiry_per_simulation() -> None:
    report = run_batch(stable_agent(), volatile_agent(), n_simulations=50, rng=random.Random(3))
    assert len(report.expiry_distribution) == 50
    assert 0.0 <= report.compatibility_score <= 1.0
    assert all(0 <= day <= MAX_DAYS for day in report.expiry_distribution)


def test_a_bad_ratio_alone_can_collapse_a_simulation_that_never_reaches_hostile(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Forces collapse_probability to certainty, regardless of the discrete EmotionalState, to
    verify the ratio-based path (DEC in docs/architecture.md, M4) actually ends the simulation on
    its own — not just alongside the Markov chain reaching COLLAPSED."""
    monkeypatch.setattr(simulation_module, "collapse_probability", lambda _ratio: 1.0)

    result = run_simulation(stable_agent(), stable_agent(), rng=random.Random(4))

    assert result.outcome == "collapsed"
    assert result.final_state.emotional_state.value != "collapsed"
