"""Builds a domain `Agent` from the wire payload. The Builder pattern the doc asks for in
section 2 ("Builder: building a synthetic Agent") — the domain never sees Pydantic models."""

from app.api import schemas
from app.domain.agent import Agent, AttachmentStyle, CommunicationProfile

_WIRE_TO_DOMAIN_ATTACHMENT: dict[schemas.AttachmentStyle, AttachmentStyle] = {
    schemas.AttachmentStyle.ANXIOUS: AttachmentStyle.ANXIOUS,
    schemas.AttachmentStyle.AVOIDANT: AttachmentStyle.AVOIDANT,
    schemas.AttachmentStyle.SECURE: AttachmentStyle.SECURE,
    schemas.AttachmentStyle.DISORGANIZED: AttachmentStyle.DISORGANIZED,
}


class AgentBuilder:
    """No instance state — a namespace for the one conversion this service needs."""

    @staticmethod
    def from_wire(params: schemas.SimulationParameters) -> Agent:
        communication_profile = CommunicationProfile(
            criticism=params.communication_profile.criticism,
            contempt=params.communication_profile.contempt,
            defensiveness=params.communication_profile.defensiveness,
            stonewalling=params.communication_profile.stonewalling,
        )
        return Agent(
            attachment_style=_WIRE_TO_DOMAIN_ATTACHMENT[params.attachment_style],
            attachment_intensity=params.attachment_intensity,
            communication_profile=communication_profile,
            infidelity_history=params.infidelity_history,
            relationship_history=params.relationship_history,
            active_addiction=params.active_addiction,
            stress_baseline=params.stress_baseline,
            commitment_pace_expectation=params.commitment_pace_expectation,
        )
