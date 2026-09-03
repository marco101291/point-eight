"""AMQP messaging: the fire-and-forget compatibility score flow (M4).

Mirrors java-system's `CompatibilityMessagingConfig` — same exchange/queue/routing-key names on
both sides, since nothing enforces that at compile time the way a shared type would. Both services
declare the full topology independently on startup; RabbitMQ declarations are idempotent, so it
doesn't matter which one runs first.
"""

import json
import logging
from dataclasses import dataclass
from typing import Any

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractRobustConnection

from app.api.schemas import CompatibilityRequest, CompatibilityResponse
from app.config import settings
from app.persistence.database import get_session
from app.services.compatibility import evaluate
from app.services.simulation_recording import SamplingRecorder, persist_run

logger = logging.getLogger(__name__)

EXCHANGE = "pointeight.compatibility"
REQUEST_QUEUE = "compatibility.score.requested"
REQUEST_ROUTING_KEY = "score.requested"
RESPONSE_QUEUE = "compatibility.score.computed"
RESPONSE_ROUTING_KEY = "score.computed"


class CompatibilityScoreRequestMessage(CompatibilityRequest):
    """What arrives on `compatibility.score.requested`. Same shape as the REST contract
    (`CompatibilityRequest`) plus `matchId`, which the REST call never needed — a synchronous
    caller already knows which match it's asking about."""

    match_id: str


@dataclass(frozen=True)
class RequestOutcome:
    """Everything the AMQP callback needs after processing one request: the typed response to
    publish back to Java, and what M5 needs to persist a `SimulationRun` for it. Keeps `response`
    as the Pydantic model rather than a plain dict, so both the wire payload and the persist_run
    call below read from the same type-checked fields instead of two independently-typed string
    keys that a rename could silently split apart."""

    match_id: str
    response: CompatibilityResponse
    n_simulations: int
    recorder: SamplingRecorder


def build_response_payload(payload: dict[str, Any]) -> RequestOutcome:
    """The actual work: parse a request payload, run the batch, shape a response payload. Pulled
    out of the AMQP callback (still fully synchronous, no DB) so it's testable without a running
    broker or database."""
    request = CompatibilityScoreRequestMessage.model_validate(payload)
    n_simulations = settings.default_simulations
    evaluation = evaluate(request, n_simulations=n_simulations)
    return RequestOutcome(
        match_id=request.match_id,
        response=evaluation.response,
        n_simulations=n_simulations,
        recorder=evaluation.recorder,
    )


class CompatibilityScoreConsumer:
    """Owns the AMQP connection for the app's lifetime — one instance, created and started in
    `main.py`'s lifespan, stopped on shutdown."""

    def __init__(self, url: str) -> None:
        self._url = url
        self._connection: AbstractRobustConnection | None = None
        self._exchange: aio_pika.abc.AbstractExchange | None = None

    async def start(self) -> None:
        self._connection = await aio_pika.connect_robust(self._url)
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=10)

        exchange = await channel.declare_exchange(
            EXCHANGE, aio_pika.ExchangeType.DIRECT, durable=True
        )
        request_queue = await channel.declare_queue(REQUEST_QUEUE, durable=True)
        await request_queue.bind(exchange, routing_key=REQUEST_ROUTING_KEY)
        # Declared here too, even though only Java ever consumes it — see the module docstring.
        response_queue = await channel.declare_queue(RESPONSE_QUEUE, durable=True)
        await response_queue.bind(exchange, routing_key=RESPONSE_ROUTING_KEY)

        self._exchange = exchange
        await request_queue.consume(self._on_request)
        logger.info("Listening on %s", REQUEST_QUEUE)

    async def stop(self) -> None:
        if self._connection is not None:
            await self._connection.close()

    async def _on_request(self, message: AbstractIncomingMessage) -> None:
        async with message.process():
            payload = json.loads(message.body)
            outcome = build_response_payload(payload)
            reply = {"matchId": outcome.match_id, **outcome.response.model_dump(by_alias=True)}
            assert self._exchange is not None  # set in start(), before any message can arrive
            await self._exchange.publish(
                aio_pika.Message(body=json.dumps(reply).encode()),
                routing_key=RESPONSE_ROUTING_KEY,
            )
            # Persisted after publishing, not before: Java doesn't wait on this either way (it's
            # fire-and-forget, DEC-016), so there's no reason to delay the reply for it. Caught,
            # not left to propagate: `message.process()` rejects (requeue=False) on any exception
            # here, which would silently and permanently drop this SimulationRun with no retry —
            # the score already reached Java either way, so a failed *persist* shouldn't also cost
            # the message.
            try:
                async with get_session() as session:
                    await persist_run(
                        session,
                        match_id=outcome.match_id,
                        model_version=outcome.response.model_version,
                        n_simulations=outcome.n_simulations,
                        compatibility_score=outcome.response.compatibility_score,
                        expiry_days=outcome.response.expiry_days,
                        recorder=outcome.recorder,
                    )
            except Exception:
                logger.exception(
                    "Could not persist SimulationRun for match %s (score was already sent)",
                    outcome.match_id,
                )
