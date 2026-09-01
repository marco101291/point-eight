package com.pointeight.user.domain;

import java.util.UUID;

/** Identity of the User aggregate. */
public record UserId(UUID value) {

  public UserId {
    if (value == null) {
      throw new IllegalArgumentException("UserId cannot be null");
    }
  }

  public static UserId newId() {
    return new UserId(UUID.randomUUID());
  }

  public static UserId of(String raw) {
    try {
      return new UserId(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid UserId: " + raw);
    }
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
