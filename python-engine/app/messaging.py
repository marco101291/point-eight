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

from app.api.schemas import CompatibilityRequest
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
    """Everything the AMQP callback needs after processing one request: the payload to publish
    back to Java, and what M5 needs to persist a `SimulationRun` for it. Doesn't duplicate
    `matchId`/`modelVersion` as separate fields — they're already in `reply_payload`, and a second
    copy would just be one more place for the two to drift apart."""

    n_simulations: int
    reply_payload: dict[str, Any]
    recorder: SamplingRecorder


def build_response_payload(payload: dict[str, Any]) -> RequestOutcome:
    """The actual work: parse a request payload, run the batch, shape a response payload. Pulled
    out of the AMQP callback (still fully synchronous, no DB) so it's testable without a running
    broker or database."""
    request = CompatibilityScoreRequestMessage.model_validate(payload)
    n_simulations = settings.default_simulations
    evaluation = evaluate(request, n_simulations=n_simulations)
    reply = {
        "matchId": request.match_id,
        "modelVersion": evaluation.response.model_version,
        "compatibilityScore": evaluation.response.compatibility_score,
        "expiryDays": evaluation.response.expiry_days,
    }
    return RequestOutcome(
        n_simulations=n_simulations,
        reply_payload=reply,
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
            assert self._exchange is not None  # set in start(), before any message can arrive
            await self._exchange.publish(
                aio_pika.Message(body=json.dumps(outcome.reply_payload).encode()),
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
                        match_id=outcome.reply_payload["matchId"],
                        model_version=outcome.reply_payload["modelVersion"],
                        n_simulations=outcome.n_simulations,
                        compatibility_score=outcome.reply_payload["compatibilityScore"],
                        expiry_days=outcome.reply_payload["expiryDays"],
                        recorder=outcome.recorder,
                    )
            except Exception:
                logger.exception(
                    "Could not persist SimulationRun for match %s (score was already sent)",
                    outcome.reply_payload["matchId"],
                )
