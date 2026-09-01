package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The only thing the outside world sees of a user.
 *
 * <p>Declares exclusively Layer 1 fields. Layer 2 — attachment, Gottman's horsemen, infidelity,
 * addiction, baseline stress — has nowhere to enter this record, so it can't leak by accident: the
 * guarantee is structural, not a list of exclusions someone has to maintain.
 */
public record UserResponse(
    String id,
    int age,
    Gender gender,
    Set<Gender> seekingGenders,
    SeekingType seekingType,
    String city,
    String profession,
    List<String> hobbies,
    double cumulativeConfidenceScore,
    Instant createdAt,
    Instant updatedAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.id().toString(),
        user.profile().age(),
        user.profile().gender(),
        user.profile().seekingGenders(),
        user.profile().seekingType(),
        user.profile().city(),
        user.profile().profession(),
        user.profile().hobbies(),
        user.cumulativeConfidenceScore().value(),
        user.createdAt(),
        user.updatedAt());
  }
}
