package com.pointeight.user.domain;

/**
 * The user's cumulative confidence in the System. Rises as assigned matches end the way the
 * System predicted.
 */
public record ConfidenceScore(double value) {

  public ConfidenceScore {
    if (value < 0.0 || value > 1.0 || Double.isNaN(value)) {
      throw new IllegalArgumentException(
          "ConfidenceScore must be between 0.0 and 1.0, got " + value);
    }
  }

  public static ConfidenceScore initial() {
    return new ConfidenceScore(0.0);
  }
}
