"""AMQP messaging: the fire-and-forget compatibility score flow (M4).

Mirrors java-system's `CompatibilityMessagingConfig` — same exchange/queue/routing-key names on
both sides, since nothing enforces that at compile time the way a shared type would. Both services
declare the full topology independently on startup; RabbitMQ declarations are idempotent, so it
doesn't matter which one runs first.
"""

import json
import logging
from typing import Any

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractRobustConnection

from app.api.schemas import CompatibilityRequest
from app.config import settings
from app.services.compatibility import evaluate

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


def build_response_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """The actual work: parse a request payload, run the batch, shape a response payload. Pulled
    out of the AMQP callback so it's testable without a running broker."""
    request = CompatibilityScoreRequestMessage.model_validate(payload)
    response = evaluate(request, n_simulations=settings.default_simulations)
    return {
        "matchId": request.match_id,
        "modelVersion": response.model_version,
        "compatibilityScore": response.compatibility_score,
        "expiryDays": response.expiry_days,
    }


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
            reply_payload = build_response_payload(payload)
            assert self._exchange is not None  # set in start(), before any message can arrive
            await self._exchange.publish(
                aio_pika.Message(body=json.dumps(reply_payload).encode()),
                routing_key=RESPONSE_ROUTING_KEY,
            )
