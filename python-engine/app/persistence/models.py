"""SQLAlchemy models for `SimulationRun` and what it needs to feed M5's two data-hungry views
(the spaghetti plot and the "learned" Markov graph) — the open question from M3's
docs/architecture.md, resolved in M5.

Split across three tables instead of one wide one because the two consumers need very different
granularity: the spaghetti plot needs a handful of *full* day-by-day trajectories (expensive, so
only sampled), the Markov graph needs *aggregated counts* across the whole batch (cheap, so kept
for every simulation, not just the sample).
"""

import uuid
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import ForeignKey, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class SimulationRun(Base):
    """One row per batch — one call to `run_batch`, i.e. one score request for a match."""

    __tablename__ = "simulation_runs"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    match_id: Mapped[str] = mapped_column(String(36), index=True)
    model_version: Mapped[str] = mapped_column(String(16))
    n_simulations: Mapped[int]
    compatibility_score: Mapped[float]
    expiry_days: Mapped[int]
    created_at: Mapped[datetime] = mapped_column(default=lambda: datetime.now(timezone.utc))

    trajectories: Mapped[list["SimulationTrajectory"]] = relationship(
        back_populates="run", cascade="all, delete-orphan"
    )
    transitions: Mapped[list["SimulationTransition"]] = relationship(
        back_populates="run", cascade="all, delete-orphan"
    )


class SimulationTrajectory(Base):
    """One row per *sampled* simulation within a run — not every simulation in the batch, or a
    1,000-simulation run would mean 1,000 rows just to feed the spaghetti plot's sample. The
    sampling policy lives in `app/services/simulation_recording.py`, not here."""

    __tablename__ = "simulation_trajectories"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    run_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("simulation_runs.id", ondelete="CASCADE"))
    simulation_index: Mapped[int]
    outcome: Mapped[str] = mapped_column(String(16))
    expiry_day: Mapped[int]
    # [{"day": 0, "trust": 0.6, "resentment": 0.0, "satisfaction": 0.5,
    #   "emotionalState": "stable"}, ...]
    points: Mapped[list[dict[str, Any]]] = mapped_column(JSONB)

    run: Mapped["SimulationRun"] = relationship(back_populates="trajectories")


class SimulationTransition(Base):
    """Aggregated per run: how many times `from_state -> to_state` actually happened across
    *every* simulation in the batch, not just the sampled ones. This is what makes the Markov
    graph "learned" instead of a display of the hand-authored `TRANSITIONS` table in
    `app/domain/state.py`."""

    __tablename__ = "simulation_transitions"
    __table_args__ = (UniqueConstraint("run_id", "from_state", "to_state"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    run_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("simulation_runs.id", ondelete="CASCADE"))
    from_state: Mapped[str] = mapped_column(String(16))
    to_state: Mapped[str] = mapped_column(String(16))
    count: Mapped[int]

    run: Mapped["SimulationRun"] = relationship(back_populates="transitions")
