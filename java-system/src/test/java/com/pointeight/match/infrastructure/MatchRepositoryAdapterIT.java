package com.pointeight.match.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.pointeight.match.domain.CompatibilityScore;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
import com.pointeight.support.PostgresIntegrationTest;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * Against a real Postgres ({@link PostgresIntegrationTest}), not a mocked {@link MatchRepository}
 * — exactly the layer a mock can't exercise. Specifically written to cover the class of bug
 * DEC-028 found the hard way: a domain mutation that's logically correct and still never reaches
 * the database because of a JPA mapping detail (a {@code @Column(updatable = false)} left over
 * from a field that used to be immutable).
 *
 * <p>Every test flushes and clears the persistence context between saving and reloading
 * ({@link TestEntityManager}). Without that, {@code findById} would just return the same managed
 * entity object still sitting in Hibernate's first-level cache — its Java field already carries
 * whatever the mapper's setter last assigned, regardless of whether the generated {@code UPDATE}
 * actually included that column. That gap is exactly how the real bug stayed invisible: the
 * in-memory value was correct right up until the database was checked directly.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MatchRepositoryAdapter.class)
class MatchRepositoryAdapterIT extends PostgresIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final Duration TWELVE_HOURS = Duration.ofHours(12);

  @Autowired private MatchRepository matches;
  @Autowired private TestEntityManager entityManager;

  private Match reload(Match match) {
    entityManager.flush();
    entityManager.clear();
    return matches.findById(match.id()).orElseThrow();
  }

  @Test
  void guardar_y_recargar_un_match_recien_propuesto_conserva_todos_sus_campos() {
    UserId alice = UserId.newId();
    UserId bob = UserId.newId();
    Match proposed = Match.propose(alice, bob, TWELVE_HOURS, CLOCK);

    matches.save(proposed);
    Match reloaded = reload(proposed);

    assertThat(reloaded.id()).isEqualTo(proposed.id());
    assertThat(reloaded.userAId()).isEqualTo(alice);
    assertThat(reloaded.userBId()).isEqualTo(bob);
    assertThat(reloaded.status()).isEqualTo(MatchStatus.PENDING);
    assertThat(reloaded.expiryDuration()).isEqualTo(TWELVE_HOURS);
    assertThat(reloaded.compatibilityScore()).isEmpty();
    assertThat(reloaded.activatedAt()).isEmpty();
  }

  @Test
  void activar_un_match_y_recargarlo_muestra_ACTIVE_y_activatedAt() {
    Match match = Match.propose(UserId.newId(), UserId.newId(), TWELVE_HOURS, CLOCK);
    matches.save(match);
    match.activate(CLOCK);
    matches.save(match);

    Match reloaded = reload(match);

    assertThat(reloaded.status()).isEqualTo(MatchStatus.ACTIVE);
    assertThat(reloaded.activatedAt()).contains(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void asignar_un_score_y_recargarlo_lo_conserva() {
    Match match = Match.propose(UserId.newId(), UserId.newId(), TWELVE_HOURS, CLOCK);
    matches.save(match);
    match.assignCompatibilityScore(CompatibilityScore.of(0.73));
    matches.save(match);

    Match reloaded = reload(match);

    assertThat(reloaded.compatibilityScore()).contains(CompatibilityScore.of(0.73));
  }

  @Test
  void cambiar_la_duracion_de_un_match_PENDING_y_recargarlo_muestra_la_nueva_duracion() {
    // The exact regression this class exists for: applyExpiryDuration() worked correctly through
    // the domain and the use case and still never reached Postgres, because
    // MatchJpaEntity#expiryDurationSeconds was @Column(updatable = false) — a leftover from when
    // expiryDuration was final. A mocked-repository test can't catch that; this one would have.
    Match match = Match.propose(UserId.newId(), UserId.newId(), TWELVE_HOURS, CLOCK);
    matches.save(match);
    match.applyExpiryDuration(Duration.ofDays(30));
    matches.save(match);

    Match reloaded = reload(match);

    assertThat(reloaded.expiryDuration()).isEqualTo(Duration.ofDays(30));
  }

  @Test
  void expirar_un_match_y_recargarlo_muestra_EXPIRED_y_endedAt() {
    Match match = Match.propose(UserId.newId(), UserId.newId(), TWELVE_HOURS, CLOCK);
    matches.save(match);
    match.activate(CLOCK);
    matches.save(match);
    match.expire(CLOCK);
    matches.save(match);

    Match reloaded = reload(match);

    assertThat(reloaded.status()).isEqualTo(MatchStatus.EXPIRED);
    assertThat(reloaded.endedAt()).contains(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void hasOpenMatch_es_verdadero_para_PENDING_y_ACTIVE_y_falso_para_terminales() {
    UserId seeker = UserId.newId();
    Match match = Match.propose(seeker, UserId.newId(), TWELVE_HOURS, CLOCK);
    matches.save(match);
    entityManager.flush();
    entityManager.clear();
    assertThat(matches.hasOpenMatch(seeker)).isTrue();

    match = matches.findById(match.id()).orElseThrow();
    match.activate(CLOCK);
    matches.save(match);
    entityManager.flush();
    entityManager.clear();
    assertThat(matches.hasOpenMatch(seeker)).isTrue();

    match = matches.findById(match.id()).orElseThrow();
    match.expire(CLOCK);
    matches.save(match);
    entityManager.flush();
    entityManager.clear();
    assertThat(matches.hasOpenMatch(seeker)).isFalse();
  }
}
