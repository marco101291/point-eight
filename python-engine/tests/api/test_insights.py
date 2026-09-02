from app.api.insights import build_markov_graph


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
