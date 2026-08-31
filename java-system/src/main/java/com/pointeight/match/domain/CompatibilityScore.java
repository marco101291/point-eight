package com.pointeight.match.domain;

/**
 * Proporción de simulaciones que sobrevivieron, 0..1. La calcula el Motor (Python) a partir de M2;
 * en M1 el match nace sin score.
 */
public record CompatibilityScore(double value) {

  public CompatibilityScore {
    if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(
          "CompatibilityScore debe estar entre 0.0 y 1.0, llegó " + value);
    }
  }

  public static CompatibilityScore of(double value) {
    return new CompatibilityScore(value);
  }
}
