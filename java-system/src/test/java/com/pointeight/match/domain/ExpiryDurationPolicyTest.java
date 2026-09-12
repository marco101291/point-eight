package com.pointeight.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExpiryDurationPolicyTest {

  private final ExpiryDurationPolicy policy =
      new ExpiryDurationPolicy(Duration.ofHours(12), Duration.ofDays(7), 1000);

  @Test
  void expiryDays_en_1_da_el_piso() {
    assertThat(policy.durationFor(1)).isEqualTo(Duration.ofHours(12));
  }

  @Test
  void expiryDays_en_el_tope_de_la_simulacion_da_el_techo() {
    assertThat(policy.durationFor(1000)).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void expiryDays_a_la_mitad_da_una_duracion_intermedia() {
    Duration midpoint = policy.durationFor(500);

    assertThat(midpoint).isGreaterThan(Duration.ofHours(12));
    assertThat(midpoint).isLessThan(Duration.ofDays(7));
  }

  @Test
  void un_expiryDays_mayor_a_la_simulacion_se_recorta_al_techo() {
    assertThat(policy.durationFor(5000)).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void un_expiryDays_no_positivo_se_recorta_al_piso() {
    assertThat(policy.durationFor(0)).isEqualTo(Duration.ofHours(12));
    assertThat(policy.durationFor(-30)).isEqualTo(Duration.ofHours(12));
  }

  @Test
  void el_techo_debe_ser_mayor_al_piso() {
    assertThatThrownBy(
            () -> new ExpiryDurationPolicy(Duration.ofDays(7), Duration.ofHours(12), 1000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void simulationMaxDays_debe_ser_mayor_a_uno() {
    assertThatThrownBy(() -> new ExpiryDurationPolicy(Duration.ofHours(12), Duration.ofDays(7), 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
