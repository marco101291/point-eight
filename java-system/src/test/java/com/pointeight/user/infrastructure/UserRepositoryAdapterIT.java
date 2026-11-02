package com.pointeight.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.pointeight.support.PostgresIntegrationTest;
import com.pointeight.user.domain.ConfidenceScore;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * Against a real Postgres, same reasoning (and same flush-and-clear before reloading) as {@code
 * MatchRepositoryAdapterIT}. Written alongside DEC-026's recalibration trigger: {@code
 * cumulativeConfidenceScore} had never changed after insert before, so nothing had ever proven an
 * {@code UPDATE} actually carries it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterIT extends PostgresIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Autowired private UserRepository users;
  @Autowired private TestEntityManager entityManager;

  private User reload(User user) {
    entityManager.flush();
    entityManager.clear();
    return users.findById(user.id()).orElseThrow();
  }

  @Test
  void un_match_observado_persiste_la_confianza_reforzada() {
    Profile profile =
        new Profile(
            30,
            Gender.FEMALE,
            Set.of(Gender.MALE),
            SeekingType.LONG_TERM,
            "Guadalajara",
            "docente",
            List.of("cine"),
            "https://picsum.photos/seed/test/900/1400");
    User user = users.save(User.register(profile, null, CLOCK));
    reload(user);

    User loaded = users.findById(user.id()).orElseThrow();
    loaded.recordObservedMatch(CLOCK);
    users.save(loaded);
    User reloaded = reload(loaded);

    assertThat(reloaded.cumulativeConfidenceScore())
        .isEqualTo(ConfidenceScore.initial().reinforced());
  }
}
