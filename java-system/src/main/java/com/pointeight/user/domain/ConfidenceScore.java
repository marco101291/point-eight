package com.pointeight.user.domain;

/**
 * Confianza acumulada del usuario en el Sistema. Sube a medida que los matches asignados terminan
 * como el Sistema predijo.
 */
public record ConfidenceScore(double value) {

  public ConfidenceScore {
    if (value < 0.0 || value > 1.0 || Double.isNaN(value)) {
      throw new IllegalArgumentException(
          "ConfidenceScore debe estar entre 0.0 y 1.0, llegó " + value);
    }
  }

  public static ConfidenceScore initial() {
    return new ConfidenceScore(0.0);
  }
}
