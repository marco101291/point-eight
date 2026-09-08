package com.pointeight.auth.domain;

import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Login credentials for a user. Deliberately not a field on {@link com.pointeight.user.domain.User}
 * — identity for logging in and the dating profile that gets matched have different lifecycles,
 * and keeping them apart makes it structurally impossible for a password hash to ever end up in a
 * response shaped around {@code User} (the same reasoning that keeps Layer 2 out of {@code
 * UserResponse}). Keyed by the {@link UserId} it authenticates, not its own identity — this is a
 * one-to-one detail of a user, not an aggregate of its own.
 */
public class Account {

  private final UserId userId;
  private final Email email;
  private final HashedPassword password;
  private final Instant createdAt;

  private Account(UserId userId, Email email, HashedPassword password, Instant createdAt) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.email = Objects.requireNonNull(email, "email");
    this.password = Objects.requireNonNull(password, "password");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static Account register(
      UserId userId, Email email, HashedPassword password, Clock clock) {
    return new Account(userId, email, password, Instant.now(clock));
  }

  /** Rehydration from persistence. Only used by the adapter's mapper. */
  public static Account rehydrate(
      UserId userId, Email email, HashedPassword password, Instant createdAt) {
    return new Account(userId, email, password, createdAt);
  }

  public UserId userId() {
    return userId;
  }

  public Email email() {
    return email;
  }

  public HashedPassword password() {
    return password;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /** Identity by the user it belongs to. */
  @Override
  public boolean equals(Object other) {
    return other instanceof Account account && userId.equals(account.userId);
  }

  @Override
  public int hashCode() {
    return userId.hashCode();
  }

  /** Deliberately omits the password hash — same reasoning as {@code User.toString()}. */
  @Override
  public String toString() {
    return "Account[userId=%s, email=%s]".formatted(userId, email);
  }
}
