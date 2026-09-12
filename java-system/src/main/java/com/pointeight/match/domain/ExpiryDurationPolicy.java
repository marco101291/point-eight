package com.pointeight.match.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Converts the Engine's predicted {@code expiryDays} (roughly 1..{@code simulationMaxDays} —
 * python-engine's own {@code MAX_DAYS}, a simulated-day count, not real time; {@code
 * simulationMaxDays} means "survived to the simulation's cap," i.e. an exceptionally strong pair)
 * into a real match window between {@code floor} (as short as a single date) and {@code ceiling}
 * (real years, for the rare pair the simulation never breaks) — a better-predicted pair gets more
 * real time together, not less.
 *
 * <p>Geometric, not linear, interpolation: {@code floor * (ceiling / floor) ^ fraction}, i.e.
 * evenly spaced in log-space rather than in raw seconds. With a floor/ceiling ratio this large
 * (hours to years), a linear scale would push every merely-average pair — not just the
 * exceptional ones — past a year, since the midpoint of the raw range already sits there. The
 * geometric curve keeps most of the distribution (mediocre to good predictions) in the
 * hours-to-weeks band that's actually usable, and reserves months-to-years for predictions
 * genuinely close to the simulation's own cap.
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
    double floorSeconds = floor.toSeconds();
    double ceilingSeconds = ceiling.toSeconds();
    double seconds = floorSeconds * Math.pow(ceilingSeconds / floorSeconds, fraction);
    return Duration.ofSeconds(Math.round(seconds));
  }
}
