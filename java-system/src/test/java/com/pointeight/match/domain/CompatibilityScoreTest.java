package com.pointeight.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompatibilityScoreTest {

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.5, 0.8, 1.0})
  void acepta_el_intervalo_unitario_incluidos_los_bordes(double value) {
    assertThat(CompatibilityScore.of(value).value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.0001, 1.0001, -1.0, 2.0, Double.NaN})
  void rechaza_todo_lo_que_este_fuera(double value) {
    assertThatThrownBy(() -> CompatibilityScore.of(value))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dos_scores_iguales_son_el_mismo_value_object() {
    assertThat(CompatibilityScore.of(0.8)).isEqualTo(CompatibilityScore.of(0.8));
  }
}
