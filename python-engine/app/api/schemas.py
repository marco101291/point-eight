"""Wire contract for `/api/v1/compatibility` — the Java -> Engine payload from DEC-009.

Split out of `app/api/compatibility.py` so `app/services` can import these without a circular
import (`compatibility.py` calls into `app/services`, `app/services` needs these types) — see
`app/api/__init__.py`: routers only translate HTTP <-> Pydantic models, no domain logic.
"""

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


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
    """Wire shape only — matches Java's enum `.name()` serialization. Not the same type as
    `app.domain.agent.AttachmentStyle`; `AgentBuilder` maps between the two."""

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


class AgentPayload(_CamelModel):
    """One side of the request. Named `AgentPayload`, not `Agent`, to stay distinct from
    `app.domain.agent.Agent` — this is wire data, that one has behavior."""

    profile: Profile
    simulation_parameters: SimulationParameters


class CompatibilityRequest(_CamelModel):
    model_version: str
    agent_a: AgentPayload
    agent_b: AgentPayload


class CompatibilityResponse(_CamelModel):
    model_version: str
    compatibility_score: float
    expiry_days: int
