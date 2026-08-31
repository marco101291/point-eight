package com.pointeight.user.domain;

import java.util.UUID;

/** Identidad del aggregate User. */
public record UserId(UUID value) {

  public UserId {
    if (value == null) {
      throw new IllegalArgumentException("UserId no puede ser nulo");
    }
  }

  public static UserId newId() {
    return new UserId(UUID.randomUUID());
  }

  public static UserId of(String raw) {
    try {
      return new UserId(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("UserId inválido: " + raw);
    }
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
