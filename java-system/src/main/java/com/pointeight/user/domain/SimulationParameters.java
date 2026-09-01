package com.pointeight.user.domain;

/**
 * Layer 2: the parameters the Engine uses to simulate this person.
 *
 * <p>Never exposed through the API. The user cannot see — or correct — the model the System has
 * of them. That opacity is deliberate and is the narrative center of the project.
 */
public record SimulationParameters(
    AttachmentStyle attachmentStyle,
    double attachmentIntensity,
    CommunicationProfile communicationProfile,
    boolean infidelityHistory,
    int relationshipHistory,
    boolean activeAddiction,
    double stressBaseline,
    double commitmentPaceExpectation) {

  public SimulationParameters {
    if (attachmentStyle == null) {
      throw new IllegalArgumentException("attachmentStyle is required");
    }
    if (communicationProfile == null) {
      throw new IllegalArgumentException("communicationProfile is required");
    }
    requireUnitInterval(attachmentIntensity, "attachmentIntensity");
    requireUnitInterval(stressBaseline, "stressBaseline");
    requireUnitInterval(commitmentPaceExpectation, "commitmentPaceExpectation");
    if (relationshipHistory < 0) {
      throw new IllegalArgumentException("relationshipHistory cannot be negative");
    }
  }

  private static void requireUnitInterval(double value, String name) {
    if (value < 0.0 || value > 1.0 || Double.isNaN(value)) {
      throw new IllegalArgumentException(
          "%s must be between 0.0 and 1.0, got %s".formatted(name, value));
    }
  }
}
