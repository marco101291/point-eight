"""What an agent brings to a single scenario."""

from dataclasses import dataclass


@dataclass(frozen=True)
class Reaction:
    """An agent's response to one scenario, split into Gottman's two axes.

    Kept as separate `positive`/`negative` charges — not a single net value — because the whole
    project is about the *ratio* between them, not just their difference.
    """

    positive: float
    negative: float

    def __post_init__(self) -> None:
        for name, value in (("positive", self.positive), ("negative", self.negative)):
            if not 0.0 <= value <= 1.0:
                raise ValueError(f"{name} must be between 0.0 and 1.0, got {value}")
