"""Compatibility router. The Java -> Engine contract from DEC-009.

`modelVersion` stays "v0" even though the model underneath just became real (M2's random stub is
gone) — Java's engine client still sends "v0", and bumping it here without a matching java-system
change would break every score request. This is exactly the versioning scenario DEC-009 was
written for; the bump to "v1" is deliberately left as its own, single-service java-system change.

Doesn't persist a `SimulationRun` (unlike the AMQP path in `app/messaging.py`, M5): this endpoint
exists specifically as a broker-free way to exercise the Engine directly (DEC-016), and its calls
aren't tied to a real match — recording them would just be test noise in the tables M5's admin
panel reads from.
"""

import asyncio

from fastapi import APIRouter, HTTPException

from app.api.schemas import CompatibilityRequest, CompatibilityResponse
from app.config import settings
from app.services.compatibility import evaluate

router = APIRouter(prefix="/api/v1", tags=["compatibility"])

SUPPORTED_MODEL_VERSION = "v0"


@router.post("/compatibility", response_model=CompatibilityResponse)
async def compatibility(request: CompatibilityRequest) -> CompatibilityResponse:
    if request.model_version != SUPPORTED_MODEL_VERSION:
        raise HTTPException(
            status_code=422,
            detail=f"Unsupported modelVersion: {request.model_version!r}",
        )

    # Off the event loop: evaluate() runs a Monte Carlo batch (~1s of pure CPU, no await points).
    # async def here means FastAPI would otherwise run it inline on the loop, stalling every other
    # in-flight request — including /health — for that full duration.
    evaluation = await asyncio.to_thread(
        evaluate, request, n_simulations=settings.default_simulations
    )
    return evaluation.response
