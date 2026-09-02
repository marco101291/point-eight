"""Compatibility router. The Java -> Engine contract from DEC-009.

`modelVersion` stays "v0" even though the model underneath just became real (M2's random stub is
gone) — Java's `EngineCompatibilityClient` still sends "v0", and bumping it here without a
matching java-system change would break every score request. This is exactly the versioning
scenario DEC-009 was written for; the bump to "v1" is deliberately left as its own,
single-service java-system change rather than bundled into this python-engine branch.
"""

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

    return evaluate(request, n_simulations=settings.default_simulations)
