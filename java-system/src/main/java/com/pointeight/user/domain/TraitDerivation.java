package com.pointeight.user.domain;

import java.util.Set;

/**
 * Derives the traits the doc marks as "derivedFromProfession/Age": the System infers them, it
 * doesn't ask. It's a pure function over Layer 1.
 */
public final class TraitDerivation {

  /** Professions with unpredictable hours or high emotional load. */
  private static final Set<String> HIGH_STRESS =
      Set.of("medico", "medica", "enfermero", "enfermera", "cirujano", "cirujana", "abogado",
          "abogada", "policia", "bombero", "bombera", "periodista", "trader", "piloto", "militar");

  /** Professions with a steady pace and bounded hours. */
  private static final Set<String> LOW_STRESS =
      Set.of("bibliotecario", "bibliotecaria", "archivista", "jardinero", "jardinera", "ceramista",
          "traductor", "traductora", "docente", "contador", "contadora");

  private TraitDerivation() {}

  /**
   * Baseline stress, 0..1. Starts from the profession and rises slightly in one's thirties, the
   * age range where career and relationship pressures pile up simultaneously the most.
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
   * Expected commitment pace, 0..1: how much the person pushes toward the next stage. Grows with
   * age and flattens out past 50.
   */
  public static double commitmentPaceExpectation(int age) {
    if (age < 25) {
      return 0.30;
    }
    if (age >= 50) {
      return 0.70;
    }
    // Linear interpolation between 25 (0.30) and 50 (0.70).
    return clamp(0.30 + ((age - 25) * (0.40 / 25.0)));
  }

  /**
   * Default Layer 2 when registration doesn't provide it: secure attachment and neutral
   * communication.
   */
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
