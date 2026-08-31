package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Lo único que el mundo exterior ve de un usuario.
 *
 * <p>Declara exclusivamente campos de Capa 1. La Capa 2 — apego, jinetes de Gottman, infidelidad,
 * adicción, estrés basal — no tiene dónde entrar en este record, así que no puede filtrarse por
 * descuido: la garantía es estructural, no una lista de exclusiones que alguien tenga que mantener.
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
