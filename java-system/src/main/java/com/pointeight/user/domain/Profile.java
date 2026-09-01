package com.pointeight.user.domain;

import java.util.List;
import java.util.Set;

/**
 * Layer 1: the user's visible data, the only fields that go out through the API and the only ones
 * that can be filtered on. Immutable Value Object.
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
      throw new IllegalArgumentException("Age must be between %d and %d, got %d"
          .formatted(MIN_AGE, MAX_AGE, age));
    }
    if (gender == null) {
      throw new IllegalArgumentException("gender is required");
    }
    if (seekingGenders == null || seekingGenders.isEmpty()) {
      throw new IllegalArgumentException("seekingGenders cannot be empty");
    }
    if (city == null || city.isBlank()) {
      throw new IllegalArgumentException("city is required");
    }
    if (profession == null || profession.isBlank()) {
      throw new IllegalArgumentException("profession is required");
    }
    seekingType = seekingType == null ? SeekingType.UNDEFINED : seekingType;
    seekingGenders = Set.copyOf(seekingGenders);
    hobbies = hobbies == null ? List.of() : List.copyOf(hobbies);
    city = city.trim();
    profession = profession.trim();
  }
}
