"""The Compatibility Engine — FastAPI entry point."""

from fastapi import FastAPI

from app.api.compatibility import router as compatibility_router
from app.api.status import router as status_router
from app.config import settings

app = FastAPI(
    title="0.8 — The Compatibility Engine",
    version=settings.version,
    description="Monte Carlo + Markov engine that estimates a simulated relationship's duration.",
)

app.include_router(status_router)
app.include_router(compatibility_router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
