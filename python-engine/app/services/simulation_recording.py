"""Turns a `run_batch` call into rows in `SimulationRun`/`SimulationTrajectory`/
`SimulationTransition` — M5's answer to the open question left in M3's docs/architecture.md.
"""

from dataclasses import dataclass, field

from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.simulation import SimulationResult
from app.domain.state import EmotionalState, RelationshipState
from app.persistence.models import SimulationRun, SimulationTrajectory, SimulationTransition

DEFAULT_SAMPLE_SIZE = 50


@dataclass
class _SampledTrajectory:
    simulation_index: int
    outcome: str = ""
    expiry_day: int = 0
    points: list[dict[str, float | int | str]] = field(default_factory=list)


class SamplingRecorder:
    """Captures full day-by-day trajectories for the first `sample_size` simulations in a batch —
    a spaghetti plot reads as "thousands of trajectories" with 50 lines just fine, and storing all
    1,000 would mean 1,000 rows (times however many scenarios each ran) just for one score
    request. Transition counts are cheap by comparison, so those get tracked for every simulation,
    not just the sampled ones — that's what makes the Markov graph "learned" instead of a display
    of the hand-authored `TRANSITIONS` table in `app/domain/state.py`.
    """

    def __init__(self, sample_size: int = DEFAULT_SAMPLE_SIZE) -> None:
        self._sample_size = sample_size
        self._trajectories: dict[int, _SampledTrajectory] = {}
        self._last_state: dict[int, EmotionalState] = {}
        self.transition_counts: dict[tuple[str, str], int] = {}

    def on_day(self, simulation_index: int, day: int, state: RelationshipState) -> None:
        # Every simulation starts at STABLE (RelationshipState.initial()) — that's the "previous"
        # state for each simulation's first day, since nothing narrates the initial state itself.
        previous = self._last_state.get(simulation_index, EmotionalState.STABLE)
        key = (previous.value, state.emotional_state.value)
        self.transition_counts[key] = self.transition_counts.get(key, 0) + 1
        self._last_state[simulation_index] = state.emotional_state

        if simulation_index < self._sample_size:
            trajectory = self._trajectories.setdefault(
                simulation_index, _SampledTrajectory(simulation_index=simulation_index)
            )
            trajectory.points.append(
                {
                    "day": day,
                    "trust": state.trust,
                    "resentment": state.resentment,
                    "satisfaction": state.satisfaction,
                    "emotionalState": state.emotional_state.value,
                }
            )

    def on_simulation_end(self, simulation_index: int, result: SimulationResult) -> None:
        trajectory = self._trajectories.get(simulation_index)
        if trajectory is not None:
            trajectory.outcome = result.outcome
            trajectory.expiry_day = result.expiry_day

    @property
    def trajectories(self) -> list[_SampledTrajectory]:
        return list(self._trajectories.values())


async def persist_run(
    session: AsyncSession,
    *,
    match_id: str,
    model_version: str,
    n_simulations: int,
    compatibility_score: float,
    recorder: SamplingRecorder,
) -> None:
    run = SimulationRun(
        match_id=match_id,
        model_version=model_version,
        n_simulations=n_simulations,
        compatibility_score=compatibility_score,
    )
    session.add(run)
    await session.flush()  # populates run.id, needed by the child rows below

    for trajectory in recorder.trajectories:
        session.add(
            SimulationTrajectory(
                run_id=run.id,
                simulation_index=trajectory.simulation_index,
                outcome=trajectory.outcome,
                expiry_day=trajectory.expiry_day,
                points=trajectory.points,
            )
        )

    for (from_state, to_state), count in recorder.transition_counts.items():
        session.add(
            SimulationTransition(
                run_id=run.id, from_state=from_state, to_state=to_state, count=count
            )
        )

    await session.commit()
