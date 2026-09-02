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

  private final MatchRepository matches = mock(MatchRepository.class);
  private final ApplyCompatibilityScoreUseCase useCase = new ApplyCompatibilityScoreUseCase(matches);

  @Test
  void assigns_the_score_and_saves() {
    Match match = Match.propose(UserId.newId(), UserId.newId(), Duration.ofHours(12), CLOCK);
    when(matches.findById(match.id())).thenReturn(Optional.of(match));
    when(matches.save(match)).thenReturn(match);

    useCase.execute(match.id(), 0.42);

    assertThat(match.compatibilityScore()).isPresent();
    assertThat(match.compatibilityScore().get().value()).isEqualTo(0.42);
    verify(matches).save(match);
  }

  @Test
  void throws_when_the_match_does_not_exist() {
    MatchId missing = MatchId.newId();
    when(matches.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(missing, 0.5))
        .isInstanceOf(MatchNotFoundException.class);
  }
}
