package com.pointeight.match.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RandomEligibleCandidateStrategyTest {

  private static User sampleUser() {
    return User.register(
        new Profile(
            30, Gender.FEMALE, Set.of(Gender.MALE), SeekingType.LONG_TERM, "Madrid", "docente",
            List.of()),
        null,
        Clock.systemUTC());
  }

  private final RandomEligibleCandidateStrategy strategy = new RandomEligibleCandidateStrategy();

  @Test
  void returns_empty_when_there_is_nobody_eligible() {
    Optional<User> result =
        strategy.selectFor(sampleUser(), List.of(), RandomGenerator.getDefault());

    assertThat(result).isEmpty();
  }

  @Test
  void picks_the_index_the_random_generator_returns() {
    User candidateA = sampleUser();
    User candidateB = sampleUser();
    RandomGenerator alwaysZero =
        new RandomGenerator() {
          @Override
          public long nextLong() {
            return 0L;
          }

          @Override
          public int nextInt(int bound) {
            return 0;
          }
        };

    Optional<User> result =
        strategy.selectFor(sampleUser(), List.of(candidateA, candidateB), alwaysZero);

    assertThat(result).contains(candidateA);
  }

  @Test
  void the_pick_always_comes_from_the_given_pool() {
    User seeker = sampleUser();
    List<User> pool = List.of(sampleUser(), sampleUser(), sampleUser());

    Optional<User> result = strategy.selectFor(seeker, pool, RandomGenerator.getDefault());

    assertThat(result).isPresent();
    assertThat(pool).contains(result.get());
  }
}
