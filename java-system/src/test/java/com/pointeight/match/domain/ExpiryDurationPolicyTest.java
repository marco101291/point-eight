package com.pointeight.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExpiryDurationPolicyTest {

  private static final Duration FLOOR = Duration.ofHours(2);
  private static final Duration CEILING = Duration.ofDays(1000);

  private final ExpiryDurationPolicy policy = new ExpiryDurationPolicy(FLOOR, CEILING, 1000);

  @Test
  void expiryDays_en_1_da_el_piso() {
    assertThat(policy.durationFor(1)).isEqualTo(FLOOR);
  }

  @Test
  void expiryDays_en_el_tope_de_la_simulacion_da_el_techo() {
    assertThat(policy.durationFor(1000)).isEqualTo(CEILING);
  }

  @Test
  void expiryDays_a_la_mitad_da_una_duracion_intermedia() {
    Duration midpoint = policy.durationFor(500);

    assertThat(midpoint).isGreaterThan(FLOOR);
    assertThat(midpoint).isLessThan(CEILING);
  }

  @Test
  void la_escala_es_geometrica_no_lineal() {
    // A linear scale over a floor..ceiling this wide would already put the midpoint (expiryDays
    // 500) past a year — geometric interpolation keeps a merely-average prediction in weeks,
    // reserving months/years for predictions genuinely close to the simulation's own cap.
    Duration midpoint = policy.durationFor(500);

    assertThat(midpoint).isLessThan(Duration.ofDays(30));
  }

  @Test
  void un_expiryDays_mayor_a_la_simulacion_se_recorta_al_techo() {
    assertThat(policy.durationFor(5000)).isEqualTo(CEILING);
  }

  @Test
  void un_expiryDays_no_positivo_se_recorta_al_piso() {
    assertThat(policy.durationFor(0)).isEqualTo(FLOOR);
    assertThat(policy.durationFor(-30)).isEqualTo(FLOOR);
  }

  @Test
  void el_techo_debe_ser_mayor_al_piso() {
    assertThatThrownBy(() -> new ExpiryDurationPolicy(CEILING, FLOOR, 1000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void simulationMaxDays_debe_ser_mayor_a_uno() {
    assertThatThrownBy(() -> new ExpiryDurationPolicy(FLOOR, CEILING, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
