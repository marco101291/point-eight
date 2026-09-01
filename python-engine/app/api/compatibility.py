"""Compatibility router. The Java → Engine contract defined in M2.

The real score (Monte Carlo + Markov) lands in M3; here the Engine only validates the envelope
and returns a random score + expiry, so the System can integrate the synchronous REST client
without waiting for the real model.
"""

import random
from enum import Enum

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

router = APIRouter(prefix="/api/v1", tags=["compatibility"])

SUPPORTED_MODEL_VERSION = "v0"

# Purely illustrative expiry range until M3, when it's replaced by the real distribution from
# `run_batch` (expiry_distribution in section 4 of the doc).
MIN_EXPIRY_DAYS = 1
MAX_EXPIRY_DAYS = 90


class _CamelModel(BaseModel):
    """All payloads travel in camelCase, to match Java's records as-is."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class Gender(str, Enum):
    FEMALE = "FEMALE"
    MALE = "MALE"
    NON_BINARY = "NON_BINARY"


class SeekingType(str, Enum):
    CASUAL = "CASUAL"
    SHORT_TERM = "SHORT_TERM"
    LONG_TERM = "LONG_TERM"
    UNDEFINED = "UNDEFINED"


class AttachmentStyle(str, Enum):
    ANXIOUS = "ANXIOUS"
    AVOIDANT = "AVOIDANT"
    SECURE = "SECURE"
    DISORGANIZED = "DISORGANIZED"


class Profile(_CamelModel):
    """Layer 1: mirror of `com.pointeight.user.domain.Profile`."""

    age: int
    gender: Gender
    seeking_genders: list[Gender]
    seeking_type: SeekingType
    city: str
    profession: str
    hobbies: list[str] = Field(default_factory=list)


class CommunicationProfile(_CamelModel):
    criticism: float = Field(ge=0.0, le=1.0)
    contempt: float = Field(ge=0.0, le=1.0)
    defensiveness: float = Field(ge=0.0, le=1.0)
    stonewalling: float = Field(ge=0.0, le=1.0)


class SimulationParameters(_CamelModel):
    """Layer 2: mirror of `com.pointeight.user.domain.SimulationParameters`.

    This is the only channel through which Layer 2 leaves the System — never in an HTTP response
    to an external client.
    """

    attachment_style: AttachmentStyle
    attachment_intensity: float = Field(ge=0.0, le=1.0)
    communication_profile: CommunicationProfile
    infidelity_history: bool
    relationship_history: int = Field(ge=0)
    active_addiction: bool
    stress_baseline: float = Field(ge=0.0, le=1.0)
    commitment_pace_expectation: float = Field(ge=0.0, le=1.0)


class Agent(_CamelModel):
    profile: Profile
    simulation_parameters: SimulationParameters


class CompatibilityRequest(_CamelModel):
    model_version: str
    agent_a: Agent
    agent_b: Agent


class CompatibilityResponse(_CamelModel):
    model_version: str
    compatibility_score: float
    expiry_days: int


@router.post("/compatibility", response_model=CompatibilityResponse)
async def compatibility(request: CompatibilityRequest) -> CompatibilityResponse:
    if request.model_version != SUPPORTED_MODEL_VERSION:
        raise HTTPException(
            status_code=422,
            detail=f"Unsupported modelVersion: {request.model_version!r}",
        )

    return CompatibilityResponse(
        model_version=request.model_version,
        compatibility_score=random.random(),
        expiry_days=random.randint(MIN_EXPIRY_DAYS, MAX_EXPIRY_DAYS),
    )
