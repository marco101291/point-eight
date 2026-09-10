package com.pointeight.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The Layer 2 the System infers without asking the user anything. */
class TraitDerivationTest {

  @ParameterizedTest
  @ValueSource(strings = {"medico", "Médica", "  ABOGADO  ", "cirujana"})
  @DisplayName("high-stress professions raise the baseline, regardless of case or accents")
  void altoEstres(String profession) {
    assertThat(TraitDerivation.stressBaseline(profession, 22)).isCloseTo(0.75, within(1e-9));
  }

  @Test
  void las_profesiones_de_bajo_estres_lo_bajan() {
    assertThat(TraitDerivation.stressBaseline("bibliotecaria", 22)).isCloseTo(0.25, within(1e-9));
  }

  @Test
  void una_profesion_desconocida_cae_al_valor_medio() {
    assertThat(TraitDerivation.stressBaseline("astronauta", 22)).isCloseTo(0.45, within(1e-9));
  }

  @Test
  @DisplayName("the 28-42 age range adds pressure")
  void ajustePorEdad() {
    assertThat(TraitDerivation.stressBaseline("astronauta", 35)).isCloseTo(0.55, within(1e-9));
    assertThat(TraitDerivation.stressBaseline("astronauta", 27)).isCloseTo(0.45, within(1e-9));
    assertThat(TraitDerivation.stressBaseline("astronauta", 43)).isCloseTo(0.45, within(1e-9));
  }

  @Test
  void el_estres_nunca_se_sale_del_intervalo_unitario() {
    for (int age = 18; age <= 120; age++) {
      double value = TraitDerivation.stressBaseline("cirujano", age);
      assertThat(value).isBetween(0.0, 1.0);
    }
  }

  @Test
  void el_ritmo_de_compromiso_crece_con_la_edad_y_se_aplana() {
    assertThat(TraitDerivation.commitmentPaceExpectation(20)).isCloseTo(0.30, within(1e-9));
    assertThat(TraitDerivation.commitmentPaceExpectation(25)).isCloseTo(0.30, within(1e-9));
    assertThat(TraitDerivation.commitmentPaceExpectation(37)).isCloseTo(0.492, within(1e-3));
    assertThat(TraitDerivation.commitmentPaceExpectation(50)).isCloseTo(0.70, within(1e-9));
    assertThat(TraitDerivation.commitmentPaceExpectation(80)).isCloseTo(0.70, within(1e-9));
  }

  @Test
  void es_monotono_no_decreciente_en_la_edad() {
    double previous = -1.0;
    for (int age = 18; age <= 120; age++) {
      double value = TraitDerivation.commitmentPaceExpectation(age);
      assertThat(value).isGreaterThanOrEqualTo(previous);
      previous = value;
    }
  }

  @Test
  @DisplayName("defaults are secure attachment, neutral communication and no red flags")
  void defaults() {
    Profile profile =
        new Profile(
            34,
            Gender.NON_BINARY,
            Set.of(Gender.FEMALE),
            SeekingType.LONG_TERM,
            "Rosario",
            "cirujana",
            List.of("ajedrez"),
            "https://picsum.photos/seed/test/900/1400");

    SimulationParameters params = TraitDerivation.defaultsFor(profile);

    assertThat(params.attachmentStyle()).isEqualTo(AttachmentStyle.SECURE);
    assertThat(params.communicationProfile()).isEqualTo(CommunicationProfile.neutral());
    assertThat(params.infidelityHistory()).isFalse();
    assertThat(params.activeAddiction()).isFalse();
    assertThat(params.relationshipHistory()).isZero();
    assertThat(params.stressBaseline())
        .as("34-year-old surgeon = high stress + pressure age range")
        .isCloseTo(0.85, within(1e-9));
  }
}
