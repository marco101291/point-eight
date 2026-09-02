package com.pointeight.match.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateEligibilityTest {

  private static User userOf(Gender gender, Gender... seeking) {
    return User.register(
        new Profile(30, gender, Set.of(seeking), SeekingType.LONG_TERM, "Madrid", "docente", List.of()),
        null,
        Clock.systemUTC());
  }

  @Test
  void excludes_the_seeker_even_if_reciprocity_would_pass() {
    User seeker = userOf(Gender.FEMALE, Gender.FEMALE);

    assertThat(CandidateEligibility.of(seeker).isSatisfiedBy(seeker)).isFalse();
  }

  @Test
  void excludes_candidates_without_mutual_interest() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    User candidate = userOf(Gender.MALE, Gender.NON_BINARY);

    assertThat(CandidateEligibility.of(seeker).isSatisfiedBy(candidate)).isFalse();
  }

  @Test
  void accepts_a_mutually_interested_stranger() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    User candidate = userOf(Gender.MALE, Gender.FEMALE);

    assertThat(CandidateEligibility.of(seeker).isSatisfiedBy(candidate)).isTrue();
  }
}
