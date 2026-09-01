"""Orchestrates one compatibility check: builds both agents, runs the batch, aggregates the
result into the wire response. The only thing `app/api/compatibility.py` delegates to."""

import statistics

from app.api import schemas
from app.domain.simulation import run_batch
from app.services.agent_builder import AgentBuilder


def evaluate(
    request: schemas.CompatibilityRequest, n_simulations: int
) -> schemas.CompatibilityResponse:
    agent_a = AgentBuilder.from_wire(request.agent_a.simulation_parameters)
    agent_b = AgentBuilder.from_wire(request.agent_b.simulation_parameters)

    report = run_batch(agent_a, agent_b, n_simulations=n_simulations)

    # The median is more representative of a typical outcome than the mean here: a handful of
    # early collapses (very small expiry_day) or max_days survivors can otherwise skew a straight
    # average. `docs/architecture.md`'s open questions still list how `SimulationRun` persists the
    # full distribution — this just picks one number for the synchronous response.
    representative_expiry = round(statistics.median(report.expiry_distribution))

    return schemas.CompatibilityResponse(
        model_version=request.model_version,
        compatibility_score=report.compatibility_score,
        expiry_days=representative_expiry,
    )
