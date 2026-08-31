package com.pointeight.user.domain;

import java.util.List;
import java.util.Set;

/**
 * Capa 1: los datos visibles del usuario, los únicos que salen por la API y los únicos sobre los
 * que se puede filtrar. Value Object inmutable.
 */
public record Profile(
    int age,
    Gender gender,
    Set<Gender> seekingGenders,
    SeekingType seekingType,
    String city,
    String profession,
    List<String> hobbies) {

  private static final int MIN_AGE = 18;
  private static final int MAX_AGE = 120;

  public Profile {
    if (age < MIN_AGE || age > MAX_AGE) {
      throw new IllegalArgumentException("La edad debe estar entre %d y %d, llegó %d"
          .formatted(MIN_AGE, MAX_AGE, age));
    }
    if (gender == null) {
      throw new IllegalArgumentException("gender es obligatorio");
    }
    if (seekingGenders == null || seekingGenders.isEmpty()) {
      throw new IllegalArgumentException("seekingGenders no puede estar vacío");
    }
    if (city == null || city.isBlank()) {
      throw new IllegalArgumentException("city es obligatoria");
    }
    if (profession == null || profession.isBlank()) {
      throw new IllegalArgumentException("profession es obligatoria");
    }
    seekingType = seekingType == null ? SeekingType.UNDEFINED : seekingType;
    seekingGenders = Set.copyOf(seekingGenders);
    hobbies = hobbies == null ? List.of() : List.copyOf(hobbies);
    city = city.trim();
    profession = profession.trim();
  }
}
