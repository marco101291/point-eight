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

class ReciprocalGenderInterestTest {

  private static User userOf(Gender gender, Gender... seeking) {
    return User.register(
        new Profile(30, gender, Set.of(seeking), SeekingType.LONG_TERM, "Madrid", "docente",
            List.of(), "https://picsum.photos/seed/test/900/1400"),
        null,
        Clock.systemUTC());
  }

  @Test
  void satisfied_when_both_sides_seek_each_other() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    User candidate = userOf(Gender.MALE, Gender.FEMALE);

    assertThat(new ReciprocalGenderInterest(seeker).isSatisfiedBy(candidate)).isTrue();
  }

  @Test
  void not_satisfied_when_the_seeker_is_not_interested_in_the_candidate() {
    User seeker = userOf(Gender.FEMALE, Gender.NON_BINARY);
    User candidate = userOf(Gender.MALE, Gender.FEMALE);

    assertThat(new ReciprocalGenderInterest(seeker).isSatisfiedBy(candidate)).isFalse();
  }

  @Test
  void not_satisfied_when_the_candidate_is_not_interested_in_the_seeker() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    User candidate = userOf(Gender.MALE, Gender.NON_BINARY);

    assertThat(new ReciprocalGenderInterest(seeker).isSatisfiedBy(candidate)).isFalse();
  }
}
