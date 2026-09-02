package com.pointeight.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserSpecificationTest {

  private static final User ANY_USER =
      User.register(
          new Profile(
              30, Gender.FEMALE, Set.of(Gender.MALE), SeekingType.LONG_TERM, "Madrid",
              "arquitecta", List.of()),
          null,
          Clock.systemUTC());

  private static final UserSpecification ALWAYS_TRUE = candidate -> true;
  private static final UserSpecification ALWAYS_FALSE = candidate -> false;

  @Test
  void and_requires_both() {
    assertThat(ALWAYS_TRUE.and(ALWAYS_TRUE).isSatisfiedBy(ANY_USER)).isTrue();
    assertThat(ALWAYS_TRUE.and(ALWAYS_FALSE).isSatisfiedBy(ANY_USER)).isFalse();
  }

  @Test
  void or_requires_either() {
    assertThat(ALWAYS_FALSE.or(ALWAYS_TRUE).isSatisfiedBy(ANY_USER)).isTrue();
    assertThat(ALWAYS_FALSE.or(ALWAYS_FALSE).isSatisfiedBy(ANY_USER)).isFalse();
  }

  @Test
  void negate_flips_it() {
    assertThat(ALWAYS_TRUE.negate().isSatisfiedBy(ANY_USER)).isFalse();
    assertThat(ALWAYS_FALSE.negate().isSatisfiedBy(ANY_USER)).isTrue();
  }
}
