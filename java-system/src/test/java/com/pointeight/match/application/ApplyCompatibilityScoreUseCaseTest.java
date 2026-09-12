package com.pointeight.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplyCompatibilityScoreUseCaseTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final long FLOOR_SECONDS = Duration.ofHours(2).toSeconds();
  private static final long CEILING_SECONDS = Duration.ofDays(1000).toSeconds();
  private static final int SIMULATION_MAX_DAYS = 1000;

  private final MatchRepository matches = mock(MatchRepository.class);
  private final ApplyCompatibilityScoreUseCase useCase =
      new ApplyCompatibilityScoreUseCase(matches, FLOOR_SECONDS, CEILING_SECONDS, SIMULATION_MAX_DAYS);

  @Test
  void assigns_the_score_and_saves() {
    Match match = Match.propose(UserId.newId(), UserId.newId(), Duration.ofHours(12), CLOCK);
    when(matches.findById(match.id())).thenReturn(Optional.of(match));
    when(matches.save(match)).thenReturn(match);

    useCase.execute(match.id(), 0.42, SIMULATION_MAX_DAYS);

    assertThat(match.compatibilityScore()).isPresent();
    assertThat(match.compatibilityScore().get().value()).isEqualTo(0.42);
    verify(matches).save(match);
  }

  @Test
  void throws_when_the_match_does_not_exist() {
    MatchId missing = MatchId.newId();
    when(matches.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(missing, 0.5, 500))
        .isInstanceOf(MatchNotFoundException.class);
  }

  @Test
  void un_match_PENDING_recibe_la_duracion_derivada_del_score() {
    Match match = Match.propose(UserId.newId(), UserId.newId(), Duration.ofHours(12), CLOCK);
    when(matches.findById(match.id())).thenReturn(Optional.of(match));
    when(matches.save(match)).thenReturn(match);

    // expiryDays at the simulation's own cap -> the policy's ceiling, 1000 days.
    useCase.execute(match.id(), 0.9, SIMULATION_MAX_DAYS);

    assertThat(match.expiryDuration()).isEqualTo(Duration.ofDays(1000));
  }

  @Test
  void un_match_ya_ACTIVE_conserva_su_duracion_aunque_llegue_el_score() {
    Match match = Match.propose(UserId.newId(), UserId.newId(), Duration.ofHours(12), CLOCK);
    match.activate(CLOCK);
    when(matches.findById(match.id())).thenReturn(Optional.of(match));
    when(matches.save(match)).thenReturn(match);

    useCase.execute(match.id(), 0.9, SIMULATION_MAX_DAYS);

    assertThat(match.expiryDuration()).isEqualTo(Duration.ofHours(12));
  }
}
