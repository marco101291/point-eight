package com.pointeight.user.domain;

/**
 * A named, composable predicate over a {@link User} — the Specification pattern the doc calls for
 * on top of Layer 1 filters. Pure Java: no repository, no query, just "does this user satisfy
 * this rule".
 */
public interface UserSpecification {

  boolean isSatisfiedBy(User candidate);

  default UserSpecification and(UserSpecification other) {
    return candidate -> this.isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
  }

  default UserSpecification or(UserSpecification other) {
    return candidate -> this.isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
  }

  default UserSpecification negate() {
    return candidate -> !this.isSatisfiedBy(candidate);
  }
}
