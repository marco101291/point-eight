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

class NotSelfTest {

  private static User sampleUser() {
    return User.register(
        new Profile(
            28, Gender.MALE, Set.of(Gender.FEMALE), SeekingType.LONG_TERM, "Madrid", "docente",
            List.of(), "https://picsum.photos/seed/test/900/1400"),
        null,
        Clock.systemUTC());
  }

  @Test
  void rejects_the_excluded_id() {
    User user = sampleUser();
    assertThat(new NotSelf(user.id()).isSatisfiedBy(user)).isFalse();
  }

  @Test
  void accepts_anyone_else() {
    User excluded = sampleUser();
    User other = sampleUser();
    assertThat(new NotSelf(excluded.id()).isSatisfiedBy(other)).isTrue();
  }
}
