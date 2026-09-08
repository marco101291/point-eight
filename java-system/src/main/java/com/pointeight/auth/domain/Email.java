package com.pointeight.auth.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** An account's login identifier. Normalized to lowercase so lookup doesn't depend on case. */
public record Email(String value) {

  private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public Email {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid email: " + value);
    }
    value = value.toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return value;
  }
}
