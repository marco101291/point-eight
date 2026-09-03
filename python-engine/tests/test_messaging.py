from typing import Any

import pytest

from app.config import settings
from app.messaging import build_response_payload


@pytest.fixture(autouse=True)
def _small_batch(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "default_simulations", 25)


def _agent() -> dict[str, Any]:
    return {
        "profile": {
            "age": 29,
            "gender": "MALE",
            "seekingGenders": ["FEMALE"],
            "seekingType": "LONG_TERM",
            "city": "Madrid",
            "profession": "docente",
            "hobbies": [],
        },
        "simulationParameters": {
            "attachmentStyle": "SECURE",
            "attachmentIntensity": 0.3,
            "communicationProfile": {
                "criticism": 0.1,
                "contempt": 0.1,
                "defensiveness": 0.1,
                "stonewalling": 0.1,
            },
            "infidelityHistory": False,
            "relationshipHistory": 1,
            "activeAddiction": False,
            "stressBaseline": 0.3,
            "commitmentPaceExpectation": 0.5,
        },
    }


def test_response_carries_the_same_match_id_as_the_request() -> None:
    payload = {
        "matchId": "9f5196b4-fa21-4ff8-a697-cca43787b42e",
        "modelVersion": "v0",
        "agentA": _agent(),
        "agentB": _agent(),
    }

    outcome = build_response_payload(payload)

    assert outcome.match_id == payload["matchId"]
    assert outcome.response.model_version == "v0"
    assert 0.0 <= outcome.response.compatibility_score <= 1.0
    assert outcome.response.expiry_days >= 0


def test_outcome_carries_a_recorder_with_transition_counts() -> None:
    payload = {
        "matchId": "9f5196b4-fa21-4ff8-a697-cca43787b42e",
        "modelVersion": "v0",
        "agentA": _agent(),
        "agentB": _agent(),
    }

    outcome = build_response_payload(payload)

    assert outcome.n_simulations == 25
    assert sum(outcome.recorder.transition_counts.values()) > 0
    assert len(outcome.recorder.trajectories) > 0


def test_rejects_a_payload_missing_layer_2() -> None:
    payload = {
        "matchId": "9f5196b4-fa21-4ff8-a697-cca43787b42e",
        "modelVersion": "v0",
        "agentA": {"profile": _agent()["profile"]},
        "agentB": _agent(),
    }

    with pytest.raises(ValueError):
        build_response_payload(payload)
