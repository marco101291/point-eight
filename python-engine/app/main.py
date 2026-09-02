"""The Compatibility Engine — FastAPI entry point."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.compatibility import router as compatibility_router
from app.api.status import router as status_router
from app.config import settings
from app.messaging import CompatibilityScoreConsumer
from app.persistence.database import init_models


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    await init_models()
    consumer = CompatibilityScoreConsumer(settings.rabbitmq_url)
    await consumer.start()
    try:
        yield
    finally:
        await consumer.stop()


app = FastAPI(
    title="0.8 — The Compatibility Engine",
    version=settings.version,
    description="Monte Carlo + Markov engine that estimates a simulated relationship's duration.",
    lifespan=lifespan,
)

app.include_router(status_router)
app.include_router(compatibility_router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
