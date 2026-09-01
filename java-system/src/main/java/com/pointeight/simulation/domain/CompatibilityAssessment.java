package com.pointeight.simulation.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * What the Engine returns for a pair of agents: a score and a suggested relationship duration. In
 * M2 the score is random; M3 replaces it with {@code run_batch}.
 *
 * <p>{@code suggestedExpiry} isn't applied to {@code Match.expiryDuration} yet — that field is
 * immutable and the System currently sets it when the match is created. Reconciling the two is
 * still open (see "Open questions" in {@code docs/architecture.md}).
 */
public record CompatibilityAssessment(double score, Duration suggestedExpiry) {

  public CompatibilityAssessment {
    if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
      throw new IllegalArgumentException(
          "score must be between 0.0 and 1.0, got " + score);
    }
    Objects.requireNonNull(suggestedExpiry, "suggestedExpiry");
    if (suggestedExpiry.isZero() || suggestedExpiry.isNegative()) {
      throw new IllegalArgumentException(
          "suggestedExpiry must be positive, got " + suggestedExpiry);
    }
  }
}
