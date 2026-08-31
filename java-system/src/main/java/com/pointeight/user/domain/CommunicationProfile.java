package com.pointeight.user.domain;

/**
 * Capa 2: pesos hacia los cuatro "jinetes" de Gottman — los patrones de comunicación bajo conflicto
 * que su laboratorio identificó como predictores de ruptura. Cada peso va de 0 a 1.
 *
 * <p>No es similitud de perfil: dos personas compatibles pueden tener perfiles muy distintos y aun
 * así sostener un ratio positivo:negativo sano bajo conflicto.
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
          "%s debe estar entre 0.0 y 1.0, llegó %s".formatted(name, value));
    }
  }

  /** Perfil neutro, usado cuando el alta no aporta Capa 2. */
  public static CommunicationProfile neutral() {
    return new CommunicationProfile(0.25, 0.25, 0.25, 0.25);
  }

  /**
   * Carga negativa agregada. El desprecio pesa más que el resto: Gottman lo señala como el predictor
   * individual más fuerte de divorcio.
   */
  public double negativityLoad() {
    return (criticism + (contempt * 2.0) + defensiveness + stonewalling) / 5.0;
  }
}
