package com.pointeight.match.domain;

/**
 * Proportion of simulations that survived, 0..1. Calculated by the Engine (Python) starting in
 * M2; in M1 the match is born without a score.
 */
public record CompatibilityScore(double value) {

  public CompatibilityScore {
    if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(
          "CompatibilityScore must be between 0.0 and 1.0, got " + value);
    }
  }

  public static CompatibilityScore of(double value) {
    return new CompatibilityScore(value);
  }
}
