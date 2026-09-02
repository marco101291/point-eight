from app.domain.simulation import SimulationResult
from app.domain.state import EmotionalState, RelationshipState
from app.services.simulation_recording import DEFAULT_SAMPLE_SIZE, SamplingRecorder


def _state(emotional_state: EmotionalState) -> RelationshipState:
    return RelationshipState(
        trust=0.5, resentment=0.1, satisfaction=0.5, emotional_state=emotional_state
    )


def test_counts_the_implicit_self_loop_from_the_initial_stable_state() -> None:
    recorder = SamplingRecorder()

    recorder.on_day(simulation_index=0, day=0, state=_state(EmotionalState.STABLE))

    assert recorder.transition_counts[("stable", "stable")] == 1


def test_counts_a_real_transition() -> None:
    recorder = SamplingRecorder()

    recorder.on_day(simulation_index=0, day=0, state=_state(EmotionalState.STABLE))
    recorder.on_day(simulation_index=0, day=5, state=_state(EmotionalState.TENSE))

    assert recorder.transition_counts[("stable", "stable")] == 1
    assert recorder.transition_counts[("stable", "tense")] == 1


def test_tracks_each_simulation_independently() -> None:
    recorder = SamplingRecorder()

    recorder.on_day(simulation_index=0, day=0, state=_state(EmotionalState.TENSE))
    recorder.on_day(simulation_index=1, day=0, state=_state(EmotionalState.STABLE))

    # Both start "from" STABLE (the implicit initial state) independently of each other.
    assert recorder.transition_counts[("stable", "tense")] == 1
    assert recorder.transition_counts[("stable", "stable")] == 1


def test_only_samples_up_to_the_configured_size() -> None:
    recorder = SamplingRecorder(sample_size=2)

    for index in range(5):
        recorder.on_day(simulation_index=index, day=0, state=_state(EmotionalState.STABLE))
        recorder.on_simulation_end(
            index,
            SimulationResult(
                expiry_day=10, outcome="survived", final_state=_state(EmotionalState.STABLE)
            ),
        )

    assert {t.simulation_index for t in recorder.trajectories} == {0, 1}


def test_default_sample_size_is_reasonable() -> None:
    assert 0 < DEFAULT_SAMPLE_SIZE <= 200


def test_trajectory_captures_the_outcome_and_expiry_day() -> None:
    recorder = SamplingRecorder()
    recorder.on_day(simulation_index=0, day=0, state=_state(EmotionalState.STABLE))

    recorder.on_simulation_end(
        0,
        SimulationResult(
            expiry_day=42, outcome="collapsed", final_state=_state(EmotionalState.COLLAPSED)
        ),
    )

    trajectory = recorder.trajectories[0]
    assert trajectory.outcome == "collapsed"
    assert trajectory.expiry_day == 42
    assert trajectory.points[0]["day"] == 0
    assert trajectory.points[0]["emotionalState"] == "stable"


def test_ignores_simulation_end_for_a_simulation_outside_the_sample() -> None:
    recorder = SamplingRecorder(sample_size=1)
    recorder.on_day(simulation_index=5, day=0, state=_state(EmotionalState.STABLE))

    # simulation_index=5 was never sampled (sample_size=1 only keeps index 0) — must not raise.
    recorder.on_simulation_end(
        5,
        SimulationResult(
            expiry_day=1, outcome="survived", final_state=_state(EmotionalState.STABLE)
        ),
    )

    assert recorder.trajectories == []
