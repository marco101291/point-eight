"""El Motor de Compatibilidad — punto de entrada FastAPI."""

from fastapi import FastAPI

from app.api.status import router as status_router
from app.config import settings

app = FastAPI(
    title="0.8 — El Motor de Compatibilidad",
    version=settings.version,
    description="Motor Monte Carlo + Markov que estima la duración de una relación simulada.",
)

app.include_router(status_router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
