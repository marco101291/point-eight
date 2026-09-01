from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.domain.simulation import MAX_DAYS
from app.main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def _small_batch(monkeypatch: pytest.MonkeyPatch) -> None:
    """The real engine runs `default_simulations` sequential simulations per request; keep it
    small here so the test suite doesn't pay for M3's realism on every run."""
    monkeypatch.setattr(settings, "default_simulations", 25)


def _agent(**overrides: Any) -> dict[str, Any]:
    base = {
        "profile": {
            "age": 31,
            "gender": "FEMALE",
            "seekingGenders": ["MALE"],
            "seekingType": "LONG_TERM",
            "city": "Buenos Aires",
            "profession": "arquitecta",
            "hobbies": ["cine", "escalada"],
        },
        "simulationParameters": {
            "attachmentStyle": "DISORGANIZED",
            "attachmentIntensity": 0.93,
            "communicationProfile": {
                "criticism": 0.9,
                "contempt": 0.85,
                "defensiveness": 0.7,
                "stonewalling": 0.8,
            },
            "infidelityHistory": True,
            "relationshipHistory": 7,
            "activeAddiction": True,
            "stressBaseline": 0.88,
            "commitmentPaceExpectation": 0.77,
        },
    }
    base.update(overrides)
    return base


def _request(model_version: str = "v0") -> dict[str, Any]:
    return {"modelVersion": model_version, "agentA": _agent(), "agentB": _agent()}


def test_returns_score_and_expiry_within_range() -> None:
    response = client.post("/api/v1/compatibility", json=_request())

    assert response.status_code == 200
    body = response.json()
    assert body["modelVersion"] == "v0"
    assert 0.0 <= body["compatibilityScore"] <= 1.0
    assert 0 <= body["expiryDays"] <= MAX_DAYS


def test_rejects_unsupported_modelVersion() -> None:
    response = client.post("/api/v1/compatibility", json=_request(model_version="v99"))

    assert response.status_code == 422


def test_rejects_payload_missing_layer_2() -> None:
    request = _request()
    del request["agentA"]["simulationParameters"]

    response = client.post("/api/v1/compatibility", json=request)

    assert response.status_code == 422
