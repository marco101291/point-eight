"""The `Agent`: a synthetic stand-in for one side of a simulated relationship.

Mirrors `com.pointeight.user.domain.AttachmentStyle`/`CommunicationProfile`/`SimulationParameters`
on the Java side, but isn't the same type — this domain must stay free of FastAPI/Pydantic
(see `app/domain/__init__.py`), so it can't just reuse the wire models from `app/api/schemas.py`.
`AgentBuilder` (in `app/services`) is what bridges the two, mirroring Java's own DTO-vs-aggregate
split (DEC-004) on this side of the wire.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from enum import Enum
from typing import TYPE_CHECKING

from app.domain.reaction import Reaction
from app.domain.state import RelationshipState

if TYPE_CHECKING:
    from app.domain.scenarios import Scenario


class AttachmentStyle(str, Enum):
    ANXIOUS = "anxious"
    AVOIDANT = "avoidant"
    SECURE = "secure"
    DISORGANIZED = "disorganized"


@dataclass(frozen=True)
class CommunicationProfile:
    """Weights toward Gottman's four "horsemen". Same shape and weighting as the Java VO."""

    criticism: float
    contempt: float
    defensiveness: float
    stonewalling: float

    def negativity_load(self) -> float:
        """Aggregate negative charge. Contempt counts double — Gottman's strongest single
        predictor of divorce — matching `CommunicationProfile.negativityLoad()` in Java."""
        return (
            self.criticism + (self.contempt * 2.0) + self.defensiveness + self.stonewalling
        ) / 5.0


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


@dataclass(frozen=True)
class Agent:
    """A synthetic person for the simulation. Built from a real user's Layer 1 + Layer 2 data,
    but never carries identity — the engine only ever sees what it needs to react."""

    attachment_style: AttachmentStyle
    attachment_intensity: float
    communication_profile: CommunicationProfile
    infidelity_history: bool
    relationship_history: int
    active_addiction: bool
    stress_baseline: float
    commitment_pace_expectation: float

    def react(self, scenario: "Scenario", state: RelationshipState, rng: random.Random) -> Reaction:
        """How this agent responds to `scenario` given the relationship's current state.

        A simplified model of real attachment-theory tendencies: anxious attachment amplifies
        threat perception under conflict, avoidant attachment suppresses emotional expression
        (except it's *more* comfortable with space, so `NeedForSpace` softens instead of sharpens
        it), secure attachment dampens both extremes, and disorganized attachment adds volatility
        instead of a consistent bias.
        """
        negative = scenario.base_negative
        positive = scenario.base_positive

        load = self.communication_profile.negativity_load()
        negative *= 0.5 + load
        positive *= 1.2 - load * 0.5

        negative *= 0.7 + self.stress_baseline * 0.6

        intensity = self.attachment_intensity
        if self.attachment_style is AttachmentStyle.ANXIOUS:
            negative *= 1.0 + 0.4 * intensity
        elif self.attachment_style is AttachmentStyle.AVOIDANT:
            positive *= 1.0 - 0.3 * intensity
            if scenario.wants_space:
                negative *= 1.0 - 0.3 * intensity
        elif self.attachment_style is AttachmentStyle.SECURE:
            negative *= 1.0 - 0.3 * intensity
            positive *= 1.0 + 0.2 * intensity
        # DISORGANIZED gets no directional bias here — its unpredictability comes from the wider
        # jitter below, not a consistent push in one direction.

        if self.infidelity_history and scenario.is_trust_breach:
            negative *= 1.3

        if self.active_addiction:
            negative *= 1.2
            positive *= 0.85

        # A little hard-won stability from having done this before.
        experience_damp = 1.0 / (1.0 + 0.05 * self.relationship_history)
        negative *= 0.85 + 0.15 * experience_damp

        jitter = 0.25 if self.attachment_style is AttachmentStyle.DISORGANIZED else 0.1
        negative = _clamp01(negative + rng.uniform(-jitter, jitter))
        positive = _clamp01(positive + rng.uniform(-jitter / 2, jitter / 2))

        return Reaction(positive=positive, negative=negative)
