package com.pointeight.user.domain;

/**
 * Layer 2: weights toward Gottman's four "horsemen" — the under-conflict communication patterns
 * his lab identified as predictors of breakup. Each weight ranges from 0 to 1.
 *
 * <p>This isn't profile similarity: two compatible people can have very different profiles and
 * still sustain a healthy positive:negative ratio under conflict.
 */
public record CommunicationProfile(
    double criticism, double contempt, double defensiveness, double stonewalling) {

  public CommunicationProfile {
    requireWeight(criticism, "criticism");
    requireWeight(contempt, "contempt");
    requireWeight(defensiveness, "defensiveness");
    requireWeight(stonewalling, "stonewalling");
  }

  private static void requireWeight(double value, String name) {
    if (value < 0.0 || value > 1.0 || Double.isNaN(value)) {
      throw new IllegalArgumentException(
          "%s must be between 0.0 and 1.0, got %s".formatted(name, value));
    }
  }

  /** Neutral profile, used when registration doesn't provide Layer 2. */
  public static CommunicationProfile neutral() {
    return new CommunicationProfile(0.25, 0.25, 0.25, 0.25);
  }

  /**
   * Aggregate negative load. Contempt weighs more than the rest: Gottman flags it as the single
   * strongest predictor of divorce.
   */
  public double negativityLoad() {
    return (criticism + (contempt * 2.0) + defensiveness + stonewalling) / 5.0;
  }
}
