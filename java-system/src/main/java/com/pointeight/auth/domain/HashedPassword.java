package com.pointeight.auth.domain;

/**
 * An already-hashed password (BCrypt, applied by the caller before this is constructed). Never
 * wraps a raw password — the raw value only ever exists as a transient {@code String} on its way
 * to a {@code PasswordEncoder}, never stored in a domain type.
 */
public record HashedPassword(String value) {

  public HashedPassword {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("HashedPassword cannot be blank");
    }
  }
}
