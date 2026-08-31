package com.pointeight.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommunicationProfileTest {

  @Test
  void acepta_los_bordes_del_intervalo() {
    assertThat(new CommunicationProfile(0.0, 0.0, 0.0, 0.0)).isNotNull();
    assertThat(new CommunicationProfile(1.0, 1.0, 1.0, 1.0)).isNotNull();
  }

  @Test
  void rechaza_pesos_fuera_de_rango_nombrando_el_jinete() {
    assertThatThrownBy(() -> new CommunicationProfile(1.5, 0.0, 0.0, 0.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("criticism");
    assertThatThrownBy(() -> new CommunicationProfile(0.0, -0.1, 0.0, 0.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contempt");
    assertThatThrownBy(() -> new CommunicationProfile(0.0, 0.0, Double.NaN, 0.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("defensiveness");
  }

  @Test
  void el_perfil_neutro_es_valido_y_simetrico() {
    CommunicationProfile neutral = CommunicationProfile.neutral();

    assertThat(neutral.criticism()).isEqualTo(neutral.contempt());
    assertThat(neutral.defensiveness()).isEqualTo(neutral.stonewalling());
  }

  @Test
  @DisplayName("el desprecio pesa el doble que el resto en la carga negativa")
  void elDesprecioPesaMas() {
    CommunicationProfile soloContempt = new CommunicationProfile(0.0, 1.0, 0.0, 0.0);
    CommunicationProfile soloCriticism = new CommunicationProfile(1.0, 0.0, 0.0, 0.0);

    assertThat(soloContempt.negativityLoad()).isCloseTo(0.4, within(1e-9));
    assertThat(soloCriticism.negativityLoad()).isCloseTo(0.2, within(1e-9));
    assertThat(soloContempt.negativityLoad()).isGreaterThan(soloCriticism.negativityLoad());
  }

  @Test
  void los_extremos_dan_carga_cero_y_uno() {
    assertThat(new CommunicationProfile(0, 0, 0, 0).negativityLoad()).isZero();
    assertThat(new CommunicationProfile(1, 1, 1, 1).negativityLoad()).isCloseTo(1.0, within(1e-9));
  }
}
