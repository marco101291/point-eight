"""Engine status router."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.config import settings

router = APIRouter(prefix="/api", tags=["status"])


class EngineStatus(BaseModel):
    service: str
    version: str
    milestone: str
    collapse_ratio: float
    message: str


@router.get("/status", response_model=EngineStatus)
async def status() -> EngineStatus:
    return EngineStatus(
        service=settings.service_name,
        version=settings.version,
        milestone=settings.milestone,
        collapse_ratio=settings.collapse_ratio,
        message="The engine is online.",
    )
