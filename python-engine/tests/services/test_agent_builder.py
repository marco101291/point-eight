from app.api import schemas
from app.domain.agent import AttachmentStyle as DomainAttachmentStyle
from app.services.agent_builder import AgentBuilder


def test_maps_wire_simulation_parameters_to_domain_agent() -> None:
    wire_params = schemas.SimulationParameters(
        attachment_style=schemas.AttachmentStyle.ANXIOUS,
        attachment_intensity=0.7,
        communication_profile=schemas.CommunicationProfile(
            criticism=0.4, contempt=0.3, defensiveness=0.2, stonewalling=0.1
        ),
        infidelity_history=True,
        relationship_history=3,
        active_addiction=False,
        stress_baseline=0.6,
        commitment_pace_expectation=0.5,
    )

    agent = AgentBuilder.from_wire(wire_params)

    assert agent.attachment_style is DomainAttachmentStyle.ANXIOUS
    assert agent.attachment_intensity == 0.7
    assert agent.communication_profile.criticism == 0.4
    assert agent.communication_profile.contempt == 0.3
    assert agent.infidelity_history is True
    assert agent.relationship_history == 3
    assert agent.active_addiction is False
    assert agent.stress_baseline == 0.6
    assert agent.commitment_pace_expectation == 0.5
