package com.pointeight.match.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.match.application.RequestCompatibilityScoreUseCase;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.event.MatchAssignedEvent;
import com.pointeight.user.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MatchAssignedEventListenerTest {

  private final RequestCompatibilityScoreUseCase requestScore =
      mock(RequestCompatibilityScoreUseCase.class);
  private final MatchAssignedEventListener listener =
      new MatchAssignedEventListener(requestScore);

  @Test
  void pide_un_score_para_el_match_recien_asignado() {
    MatchId matchId = MatchId.newId();
    MatchAssignedEvent event =
        new MatchAssignedEvent(
            matchId, UserId.newId(), UserId.newId(), Duration.ofHours(12), Instant.now());

    listener.on(event);

    verify(requestScore).execute(matchId);
  }

  @Test
  void una_falla_al_pedir_el_score_no_se_propaga() {
    MatchId matchId = MatchId.newId();
    MatchAssignedEvent event =
        new MatchAssignedEvent(
            matchId, UserId.newId(), UserId.newId(), Duration.ofHours(12), Instant.now());
    when(requestScore.execute(matchId)).thenThrow(new RuntimeException("boom"));

    listener.on(event);

    verify(requestScore).execute(matchId);
  }
}
