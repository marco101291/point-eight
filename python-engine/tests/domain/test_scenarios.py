import random

from app.domain.agent import Agent, AttachmentStyle, CommunicationProfile
from app.domain.reaction import Reaction
from app.domain.scenarios import MoneyConflict, Routine, update_state
from app.domain.state import RelationshipState


def secure_agent() -> Agent:
    return Agent(
        attachment_style=AttachmentStyle.SECURE,
        attachment_intensity=0.3,
        communication_profile=CommunicationProfile(0.1, 0.1, 0.1, 0.1),
        infidelity_history=False,
        relationship_history=2,
        active_addiction=False,
        stress_baseline=0.3,
        commitment_pace_expectation=0.5,
    )


def test_resolve_returns_new_state_and_both_reactions() -> None:
    rng = random.Random(5)
    state = RelationshipState.initial()
    agent_a, agent_b = secure_agent(), secure_agent()

    outcome = Routine().resolve(state, agent_a, agent_b, rng)

    assert isinstance(outcome.state, RelationshipState)
    assert 0.0 <= outcome.reaction_a.positive <= 1.0
    assert 0.0 <= outcome.reaction_b.negative <= 1.0


def test_routine_tends_to_raise_trust_more_than_money_conflict() -> None:
    rng = random.Random(11)
    state = RelationshipState.initial()
    agent_a, agent_b = secure_agent(), secure_agent()

    routine_state = Routine().resolve(state, agent_a, agent_b, rng).state
    conflict_state = MoneyConflict().resolve(state, agent_a, agent_b, rng).state

    assert routine_state.trust >= conflict_state.trust
    assert routine_state.resentment <= conflict_state.resentment


def test_update_state_clamps_to_unit_interval_after_many_negative_days() -> None:
    rng = random.Random(9)
    state = RelationshipState.initial()
    harsh = Reaction(positive=0.0, negative=1.0)
    for _ in range(200):
        if state.is_collapsed:
            break
        state = update_state(state, harsh, harsh, rng)
        assert 0.0 <= state.trust <= 1.0
        assert 0.0 <= state.resentment <= 1.0
        assert 0.0 <= state.satisfaction <= 1.0
