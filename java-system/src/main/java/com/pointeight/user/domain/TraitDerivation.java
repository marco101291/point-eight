package com.pointeight.user.domain;

import java.util.Set;

/**
 * Deriva los rasgos que el doc marca como "derivedFromProfession/Age": el Sistema los infiere, no
 * los pregunta. Es una función pura sobre la Capa 1.
 */
public final class TraitDerivation {

  /** Profesiones con jornada impredecible o carga emocional alta. */
  private static final Set<String> HIGH_STRESS =
      Set.of("medico", "medica", "enfermero", "enfermera", "cirujano", "cirujana", "abogado",
          "abogada", "policia", "bombero", "bombera", "periodista", "trader", "piloto", "militar");

  /** Profesiones de ritmo estable y horario acotado. */
  private static final Set<String> LOW_STRESS =
      Set.of("bibliotecario", "bibliotecaria", "archivista", "jardinero", "jardinera", "ceramista",
          "traductor", "traductora", "docente", "contador", "contadora");

  private TraitDerivation() {}

  /**
   * Estrés basal, 0..1. Arranca de la profesión y sube levemente en la treintena, la franja donde
   * más se acumulan presiones simultáneas de carrera y pareja.
   */
  public static double stressBaseline(String profession, int age) {
    String key = normalize(profession);
    double base = 0.45;
    if (HIGH_STRESS.contains(key)) {
      base = 0.75;
    } else if (LOW_STRESS.contains(key)) {
      base = 0.25;
    }
    double ageAdjustment = (age >= 28 && age <= 42) ? 0.10 : 0.0;
    return clamp(base + ageAdjustment);
  }

  /**
   * Ritmo esperado de compromiso, 0..1: cuánto empuja la persona hacia la próxima etapa. Crece con
   * la edad y se aplana pasados los 50.
   */
  public static double commitmentPaceExpectation(int age) {
    if (age < 25) {
      return 0.30;
    }
    if (age >= 50) {
      return 0.70;
    }
    // Interpolación lineal entre los 25 (0.30) y los 50 (0.70).
    return clamp(0.30 + ((age - 25) * (0.40 / 25.0)));
  }

  /** Capa 2 por defecto cuando el alta no la aporta: apego seguro y comunicación neutra. */
  public static SimulationParameters defaultsFor(Profile profile) {
    return new SimulationParameters(
        AttachmentStyle.SECURE,
        0.5,
        CommunicationProfile.neutral(),
        false,
        0,
        false,
        stressBaseline(profile.profession(), profile.age()),
        commitmentPaceExpectation(profile.age()));
  }

  private static String normalize(String profession) {
    String lower = profession == null ? "" : profession.trim().toLowerCase();
    return lower
        .replace('á', 'a')
        .replace('é', 'e')
        .replace('í', 'i')
        .replace('ó', 'o')
        .replace('ú', 'u');
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
