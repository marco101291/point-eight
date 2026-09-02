"""Orchestrates one compatibility check: builds both agents, runs the batch, aggregates the
result into the wire response. The only thing `app/api/compatibility.py` and the AMQP consumer
(`app/messaging.py`) delegate to."""

import statistics
from dataclasses import dataclass

from app.api import schemas
from app.domain.simulation import run_batch
from app.services.agent_builder import AgentBuilder
from app.services.simulation_recording import SamplingRecorder


@dataclass(frozen=True)
class Evaluation:
    """The wire response, plus what M5 needs to persist a `SimulationRun` — bundled so callers
    that don't care about persistence (the REST endpoint, kept as a broker-free way to exercise
    the Engine directly per DEC-016) can just take `.response` and ignore the rest."""

    response: schemas.CompatibilityResponse
    recorder: SamplingRecorder


def evaluate(request: schemas.CompatibilityRequest, n_simulations: int) -> Evaluation:
    agent_a = AgentBuilder.from_wire(request.agent_a.simulation_parameters)
    agent_b = AgentBuilder.from_wire(request.agent_b.simulation_parameters)

    recorder = SamplingRecorder()
    report = run_batch(agent_a, agent_b, n_simulations=n_simulations, observer=recorder)

    # The median is more representative of a typical outcome than the mean here: a handful of
    # early collapses (very small expiry_day) or max_days survivors can otherwise skew a straight
    # average.
    representative_expiry = round(statistics.median(report.expiry_distribution))

    response = schemas.CompatibilityResponse(
        model_version=request.model_version,
        compatibility_score=report.compatibility_score,
        expiry_days=representative_expiry,
    )
    return Evaluation(response=response, recorder=recorder)
