import uuid
from datetime import datetime, timezone

from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.insights import build_markov_graph
from app.persistence.models import SimulationRun, SimulationTrajectory, SimulationTransition


def test_normalizes_counts_into_probabilities_per_source_state() -> None:
    graph = build_markov_graph(
        [
            ("stable", "stable", 90),
            ("stable", "tense", 10),
            ("tense", "stable", 5),
            ("tense", "tense", 5),
        ]
    )

    by_pair = {(t.from_state, t.to_state): t.probability for t in graph.transitions}
    assert by_pair[("stable", "stable")] == 0.9
    assert by_pair[("stable", "tense")] == 0.1
    assert by_pair[("tense", "stable")] == 0.5
    assert by_pair[("tense", "tense")] == 0.5


def test_sums_counts_from_multiple_runs_for_the_same_pair() -> None:
    graph = build_markov_graph(
        [
            ("stable", "tense", 3),
            ("stable", "tense", 7),  # a second run observed the same transition
            ("stable", "stable", 90),
        ]
    )

    by_pair = {(t.from_state, t.to_state): t.count for t in graph.transitions}
    assert by_pair[("stable", "tense")] == 10


def test_probabilities_for_each_source_state_sum_to_one() -> None:
    graph = build_markov_graph(
        [
            ("hostile", "hostile", 45),
            ("hostile", "tense", 20),
            ("hostile", "repairing", 25),
            ("hostile", "collapsed", 10),
        ]
    )

    total = sum(t.probability for t in graph.transitions if t.from_state == "hostile")
    assert abs(total - 1.0) < 1e-9


def test_states_list_has_no_duplicates() -> None:
    graph = build_markov_graph(
        [
            ("stable", "stable", 1),
            ("stable", "tense", 1),
            ("tense", "stable", 1),
        ]
    )

    assert graph.states == sorted(set(graph.states))
    assert graph.states == ["stable", "tense"]


def test_empty_input_produces_an_empty_graph() -> None:
    graph = build_markov_graph([])

    assert graph.states == []
    assert graph.transitions == []
    assert graph.total_observations == 0


def _run(**overrides: object) -> SimulationRun:
    defaults: dict[str, object] = {
        "id": uuid.uuid4(),
        "match_id": "match-1",
        "model_version": "v0",
        "n_simulations": 10,
        "compatibility_score": 0.5,
        "expiry_days": 10,
    }
    defaults.update(overrides)
    return SimulationRun(**defaults)


async def test_markov_graph_endpoint_sums_counts_in_sql_across_runs(
    client: AsyncClient, db_session: AsyncSession
) -> None:
    """Exercises the actual GROUP BY/SUM query, not just build_markov_graph() in isolation —
    that's the part a unit test on the pure helper can't catch a regression in."""
    run_a = _run(match_id="match-a")
    run_b = _run(match_id="match-b")
    db_session.add_all([run_a, run_b])
    await db_session.flush()
    db_session.add_all(
        [
            SimulationTransition(run_id=run_a.id, from_state="stable", to_state="stable", count=90),
            SimulationTransition(run_id=run_a.id, from_state="stable", to_state="tense", count=10),
            SimulationTransition(run_id=run_b.id, from_state="stable", to_state="tense", count=5),
        ]
    )
    await db_session.commit()

    response = await client.get("/api/v1/markov-graph")

    assert response.status_code == 200
    body = response.json()
    by_pair = {(t["fromState"], t["toState"]): t["count"] for t in body["transitions"]}
    assert by_pair[("stable", "tense")] == 15  # summed across both runs, in SQL
    assert body["totalObservations"] == 105


async def test_trajectories_endpoint_returns_the_most_recently_created_run(
    client: AsyncClient, db_session: AsyncSession
) -> None:
    older = _run(
        match_id="match-1",
        compatibility_score=0.4,
        created_at=datetime(2020, 1, 1, tzinfo=timezone.utc),
    )
    newer = _run(
        match_id="match-1",
        compatibility_score=0.6,
        created_at=datetime(2021, 1, 1, tzinfo=timezone.utc),
    )
    db_session.add_all([older, newer])
    await db_session.flush()
    db_session.add(
        SimulationTrajectory(
            run_id=newer.id,
            simulation_index=0,
            outcome="survived",
            expiry_day=8,
            points=[
                {
                    "day": 0,
                    "trust": 0.5,
                    "resentment": 0.0,
                    "satisfaction": 0.5,
                    "emotionalState": "stable",
                }
            ],
        )
    )
    await db_session.commit()

    response = await client.get("/api/v1/matches/match-1/trajectories")

    assert response.status_code == 200
    body = response.json()
    assert body["runId"] == str(newer.id)
    assert body["compatibilityScore"] == 0.6
    assert len(body["trajectories"]) == 1


async def test_trajectories_endpoint_404s_for_a_match_with_no_run(client: AsyncClient) -> None:
    response = await client.get("/api/v1/matches/does-not-exist/trajectories")

    assert response.status_code == 404
