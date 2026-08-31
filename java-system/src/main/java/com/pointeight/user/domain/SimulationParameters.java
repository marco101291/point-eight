package com.pointeight.user.domain;

/**
 * Capa 2: los parámetros con los que el Motor simula a esta persona.
 *
 * <p>Nunca se exponen por la API. El usuario no puede ver — ni corregir — el modelo que el Sistema
 * tiene de él. Esa opacidad es deliberada y es el centro narrativo del proyecto.
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
      throw new IllegalArgumentException("attachmentStyle es obligatorio");
    }
    if (communicationProfile == null) {
      throw new IllegalArgumentException("communicationProfile es obligatorio");
    }
    requireUnitInterval(attachmentIntensity, "attachmentIntensity");
    requireUnitInterval(stressBaseline, "stressBaseline");
    requireUnitInterval(commitmentPaceExpectation, "commitmentPaceExpectation");
    if (relationshipHistory < 0) {
      throw new IllegalArgumentException("relationshipHistory no puede ser negativo");
    }
  }

  private static void requireUnitInterval(double value, String name) {
    if (value < 0.0 || value > 1.0 || Double.isNaN(value)) {
      throw new IllegalArgumentException(
          "%s debe estar entre 0.0 y 1.0, llegó %s".formatted(name, value));
    }
  }
}
