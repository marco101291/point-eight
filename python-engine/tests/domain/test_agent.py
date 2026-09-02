import random
import statistics

import pytest

from app.domain.agent import Agent, AttachmentStyle, CommunicationProfile
from app.domain.scenarios import MoneyConflict, NeedForSpace, Scenario, TrustBreach
from app.domain.state import RelationshipState


def make_agent(
    attachment_style: AttachmentStyle = AttachmentStyle.SECURE,
    attachment_intensity: float = 0.5,
    infidelity_history: bool = False,
    relationship_history: int = 1,
    active_addiction: bool = False,
    stress_baseline: float = 0.4,
    negativity: float = 0.2,
) -> Agent:
    return Agent(
        attachment_style=attachment_style,
        attachment_intensity=attachment_intensity,
        communication_profile=CommunicationProfile(
            criticism=negativity,
            contempt=negativity,
            defensiveness=negativity,
            stonewalling=negativity,
        ),
        infidelity_history=infidelity_history,
        relationship_history=relationship_history,
        active_addiction=active_addiction,
        stress_baseline=stress_baseline,
        commitment_pace_expectation=0.5,
    )


def average_negative(agent: Agent, scenario: Scenario, trials: int = 500) -> float:
    rng = random.Random(7)
    state = RelationshipState.initial()
    reactions = [agent.react(scenario, state, rng).negative for _ in range(trials)]
    return statistics.mean(reactions)


@pytest.mark.parametrize("trials", [500])
def test_reaction_stays_within_unit_interval(trials: int) -> None:
    agent = make_agent()
    rng = random.Random(3)
    state = RelationshipState.initial()
    for _ in range(trials):
        reaction = agent.react(MoneyConflict(), state, rng)
        assert 0.0 <= reaction.positive <= 1.0
        assert 0.0 <= reaction.negative <= 1.0


def test_anxious_reacts_more_negatively_to_conflict_than_secure() -> None:
    anxious = make_agent(attachment_style=AttachmentStyle.ANXIOUS, attachment_intensity=0.9)
    secure = make_agent(attachment_style=AttachmentStyle.SECURE, attachment_intensity=0.9)

    assert average_negative(anxious, MoneyConflict()) > average_negative(secure, MoneyConflict())


def test_avoidant_gets_an_extra_discount_specifically_for_need_for_space() -> None:
    """Both styles react less to NeedForSpace than to MoneyConflict (it has a lower base
    negative charge) — but avoidant gets an *additional* discount on top of that, since space is
    comfortable for avoidant attachment rather than threatening. Secure doesn't get that specific
    bonus, even though secure's general dampening happens to match avoidant's numerically here —
    so the fair comparison is the relative drop between scenarios, not the raw values."""
    avoidant = make_agent(attachment_style=AttachmentStyle.AVOIDANT, attachment_intensity=0.9)
    secure = make_agent(attachment_style=AttachmentStyle.SECURE, attachment_intensity=0.9)

    avoidant_ratio = average_negative(avoidant, NeedForSpace()) / average_negative(
        avoidant, MoneyConflict()
    )
    secure_ratio = average_negative(secure, NeedForSpace()) / average_negative(
        secure, MoneyConflict()
    )
    assert avoidant_ratio < secure_ratio


def test_infidelity_history_worsens_trust_breach_specifically() -> None:
    with_history = make_agent(infidelity_history=True)
    without_history = make_agent(infidelity_history=False)

    breach_delta = average_negative(with_history, TrustBreach()) - average_negative(
        without_history, TrustBreach()
    )
    money_delta = average_negative(with_history, MoneyConflict()) - average_negative(
        without_history, MoneyConflict()
    )
    assert breach_delta > 0
    assert breach_delta > money_delta


def test_active_addiction_raises_negativity() -> None:
    with_addiction = make_agent(active_addiction=True)
    without_addiction = make_agent(active_addiction=False)

    assert average_negative(with_addiction, MoneyConflict()) > average_negative(
        without_addiction, MoneyConflict()
    )
