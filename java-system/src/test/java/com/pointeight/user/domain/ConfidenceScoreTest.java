package com.pointeight.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ConfidenceScoreTest {

  @Test
  void rechaza_valores_fuera_de_rango_o_NaN() {
    assertThatThrownBy(() -> new ConfidenceScore(-0.01))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ConfidenceScore(1.01))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ConfidenceScore(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void un_refuerzo_cierra_el_20_por_ciento_de_lo_que_falta() {
    assertThat(ConfidenceScore.initial().reinforced().value()).isCloseTo(0.2, within(1e-9));
    assertThat(new ConfidenceScore(0.5).reinforced().value()).isCloseTo(0.6, within(1e-9));
  }

  @Test
  void tras_n_refuerzos_vale_1_menos_0_8_a_la_n() {
    ConfidenceScore score = ConfidenceScore.initial();
    for (int i = 0; i < 10; i++) {
      score = score.reinforced();
    }

    assertThat(score.value()).isCloseTo(1.0 - Math.pow(0.8, 10), within(1e-9));
  }

  @Test
  void nunca_pasa_de_1() {
    ConfidenceScore score = ConfidenceScore.initial();
    for (int i = 0; i < 1_000; i++) {
      score = score.reinforced();
    }

    assertThat(score.value()).isLessThanOrEqualTo(1.0);
    assertThat(new ConfidenceScore(1.0).reinforced().value()).isEqualTo(1.0);
  }

  @Test
  void reforzar_no_muta_el_original() {
    ConfidenceScore original = new ConfidenceScore(0.3);

    original.reinforced();

    assertThat(original.value()).isEqualTo(0.3);
  }
}
