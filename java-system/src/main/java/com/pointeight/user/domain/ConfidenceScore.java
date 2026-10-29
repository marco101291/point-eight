package com.pointeight.user.domain;

/**
 * How sure the System is of its own model of the user — not how well their matches went. Every
 * match that actually ran (DEC-026: ended {@code EXPIRED}, or {@code REJECTED} after being {@code
 * ACTIVE}) is one more observation, whatever its outcome, so the score only ever rises.
 */
public record ConfidenceScore(double value) {

  /**
   * Share of the remaining uncertainty that survives each observed match. Closing a fixed fraction
   * of the gap to 1.0, rather than adding a fixed step, gives diminishing returns and can never
   * overshoot: after n matches the score is {@code 1 - 0.8^n} (1 → 0.20, 5 → 0.67, 10 → 0.89).
   */
  static final double UNCERTAINTY_RETAINED_PER_MATCH = 0.8;

  public ConfidenceScore {
    if (value < 0.0 || value > 1.0 || Double.isNaN(value)) {
      throw new IllegalArgumentException(
          "ConfidenceScore must be between 0.0 and 1.0, got " + value);
    }
  }

  public static ConfidenceScore initial() {
    return new ConfidenceScore(0.0);
  }

  /** One more observed match. Returns a new score: records are immutable. */
  public ConfidenceScore reinforced() {
    return new ConfidenceScore(1.0 - (1.0 - value) * UNCERTAINTY_RETAINED_PER_MATCH);
  }
}
