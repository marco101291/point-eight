import pytest

from app.domain.reaction import Reaction


def test_accepts_the_unit_interval() -> None:
    reaction = Reaction(positive=0.0, negative=1.0)
    assert reaction.positive == 0.0
    assert reaction.negative == 1.0


@pytest.mark.parametrize("value", [-0.001, 1.001, -1.0, 2.0])
def test_rejects_out_of_range_positive(value: float) -> None:
    with pytest.raises(ValueError):
        Reaction(positive=value, negative=0.5)


@pytest.mark.parametrize("value", [-0.001, 1.001, -1.0, 2.0])
def test_rejects_out_of_range_negative(value: float) -> None:
    with pytest.raises(ValueError):
        Reaction(positive=0.5, negative=value)
