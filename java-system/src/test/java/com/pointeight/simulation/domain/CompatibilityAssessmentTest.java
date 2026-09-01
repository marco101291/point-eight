package com.pointeight.simulation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompatibilityAssessmentTest {

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.42, 1.0})
  void acepta_scores_dentro_del_intervalo_unitario(double score) {
    var assessment = new CompatibilityAssessment(score, Duration.ofDays(14));
    assertThat(assessment.score()).isEqualTo(score);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
  void rechaza_scores_fuera_del_intervalo(double score) {
    assertThatThrownBy(() -> new CompatibilityAssessment(score, Duration.ofDays(14)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rechaza_expiry_no_positiva() {
    assertThatThrownBy(() -> new CompatibilityAssessment(0.5, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CompatibilityAssessment(0.5, Duration.ofDays(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
