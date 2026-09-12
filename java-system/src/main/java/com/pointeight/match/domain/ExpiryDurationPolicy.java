package com.pointeight.match.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Converts the Engine's predicted {@code expiryDays} (roughly 1..{@code simulationMaxDays} —
 * python-engine's own {@code MAX_DAYS}, a simulated-day count, not real time; {@code
 * simulationMaxDays} means "survived to the simulation's cap," i.e. a strong pair) into a real
 * match window between {@code floor} and {@code ceiling}: a linear scale, so a better-predicted
 * pair gets more real time together, not less. The floor covers a weak pair (an early predicted
 * collapse) and roughly matches the flat default every match starts with before a score arrives;
 * the ceiling is deliberately still days, not weeks or months — long enough to feel like more than
 * the demo cadence, short enough that a countdown UI built around hours/days doesn't need
 * rethinking for this alone (see the still-open "duration display at scale" question).
 */
public record ExpiryDurationPolicy(Duration floor, Duration ceiling, int simulationMaxDays) {

  public ExpiryDurationPolicy {
    Objects.requireNonNull(floor, "floor");
    Objects.requireNonNull(ceiling, "ceiling");
    if (floor.isZero() || floor.isNegative()) {
      throw new IllegalArgumentException("floor must be positive, got " + floor);
    }
    if (ceiling.compareTo(floor) <= 0) {
      throw new IllegalArgumentException(
          "ceiling (%s) must be greater than floor (%s)".formatted(ceiling, floor));
    }
    if (simulationMaxDays <= 1) {
      throw new IllegalArgumentException(
          "simulationMaxDays must be greater than 1, got " + simulationMaxDays);
    }
  }

  /** {@code expiryDays} is clamped to {@code [1, simulationMaxDays]} before scaling. */
  public Duration durationFor(int expiryDays) {
    int clamped = Math.max(1, Math.min(expiryDays, simulationMaxDays));
    double fraction = (clamped - 1) / (double) (simulationMaxDays - 1);
    long floorNanos = floor.toNanos();
    long ceilingNanos = ceiling.toNanos();
    return Duration.ofNanos(floorNanos + Math.round((ceilingNanos - floorNanos) * fraction));
  }
}
