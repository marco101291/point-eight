package com.pointeight.match.domain;

import java.util.UUID;

public record MatchId(UUID value) {

  public MatchId {
    if (value == null) {
      throw new IllegalArgumentException("MatchId no puede ser nulo");
    }
  }

  public static MatchId newId() {
    return new MatchId(UUID.randomUUID());
  }

  public static MatchId of(String raw) {
    try {
      return new MatchId(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("MatchId inválido: " + raw);
    }
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
