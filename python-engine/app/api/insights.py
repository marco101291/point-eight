"""Read-only endpoints for M5's admin panel: the learned Markov graph and one match's sampled
trajectories. Not part of the Java <-> Engine contract (DEC-009) — a different audience, the panel
itself — but kept in the same camelCase convention as that contract for consistency.
"""

from collections.abc import AsyncGenerator, Iterable
from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.schemas import CamelModel
from app.persistence.database import get_session
from app.persistence.models import SimulationRun, SimulationTransition

router = APIRouter(prefix="/api/v1", tags=["insights"])


async def get_db_session() -> AsyncGenerator[AsyncSession]:
    async with get_session() as session:
        yield session


class MarkovTransition(CamelModel):
    from_state: str
    to_state: str
    count: int
    probability: float


class MarkovGraph(CamelModel):
    states: list[str]
    transitions: list[MarkovTransition]
    total_observations: int


def build_markov_graph(counts: Iterable[tuple[str, str, int]]) -> MarkovGraph:
    """Aggregates raw (from_state, to_state, count) triples — one per `simulation_transitions`
    row, across every run ever recorded — into a probability per source state. This is the
    "learned" matrix DEC-017 exists to produce, as opposed to the hand-authored `TRANSITIONS`
    table in `app/domain/state.py`.

    Takes plain triples rather than ORM rows so it's testable without a database — the endpoint
    below is the only thing that knows `SimulationTransition` exists. One pass is enough: each
    triple updates both the pair total and its source-state total at once.
    """
    aggregated: dict[tuple[str, str], int] = {}
    totals_by_source: dict[str, int] = {}
    for from_state, to_state, count in counts:
        key = (from_state, to_state)
        aggregated[key] = aggregated.get(key, 0) + count
        totals_by_source[from_state] = totals_by_source.get(from_state, 0) + count

    transitions = [
        MarkovTransition(
            from_state=from_state,
            to_state=to_state,
            count=count,
            probability=count / totals_by_source[from_state],
        )
        for (from_state, to_state), count in sorted(aggregated.items())
    ]
    states = sorted({state for pair in aggregated for state in pair})

    return MarkovGraph(
        states=states,
        transitions=transitions,
        total_observations=sum(aggregated.values()),
    )


@router.get("/markov-graph", response_model=MarkovGraph)
async def markov_graph(session: AsyncSession = Depends(get_db_session)) -> MarkovGraph:
    """Sums in SQL, not in Python: `simulation_transitions` grows with every scored match and
    never gets pruned, so this stays a handful of rows regardless of how much history there is,
    instead of pulling the whole table over the wire on every page load."""
    stmt = select(
        SimulationTransition.from_state,
        SimulationTransition.to_state,
        func.sum(SimulationTransition.count).label("count"),
    ).group_by(SimulationTransition.from_state, SimulationTransition.to_state)
    result = await session.execute(stmt)
    return build_markov_graph(tuple(row) for row in result.all())


class Trajectory(CamelModel):
    simulation_index: int
    outcome: str
    expiry_day: int
    # Stored (and returned) already camelCased — see SamplingRecorder.on_day.
    points: list[dict[str, Any]]


class MatchTrajectories(CamelModel):
    match_id: str
    run_id: str
    compatibility_score: float
    trajectories: list[Trajectory]


@router.get("/matches/{match_id}/trajectories", response_model=MatchTrajectories)
async def match_trajectories(
    match_id: str, session: AsyncSession = Depends(get_db_session)
) -> MatchTrajectories:
    """The most recent run for this match, not every run — a match can be scored more than once
    (a retry after a failure, say), and the spaghetti plot only makes sense for one batch at a
    time."""
    stmt = (
        select(SimulationRun)
        .where(SimulationRun.match_id == match_id)
        .options(selectinload(SimulationRun.trajectories))
        .order_by(SimulationRun.created_at.desc())
        .limit(1)
    )
    result = await session.execute(stmt)
    run = result.scalar_one_or_none()

    if run is None:
        raise HTTPException(
            status_code=404, detail=f"No simulation run found for match {match_id!r}"
        )

    return MatchTrajectories(
        match_id=run.match_id,
        run_id=str(run.id),
        compatibility_score=run.compatibility_score,
        trajectories=[
            Trajectory(
                simulation_index=t.simulation_index,
                outcome=t.outcome,
                expiry_day=t.expiry_day,
                points=t.points,
            )
            for t in run.trajectories
        ],
    )
