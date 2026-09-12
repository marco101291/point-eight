package com.pointeight.simulation.infrastructure;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pointeight.match.application.ApplyCompatibilityScoreUseCase;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import org.junit.jupiter.api.Test;

class CompatibilityScoreResponseListenerTest {

  private final ApplyCompatibilityScoreUseCase applyScore = mock(ApplyCompatibilityScoreUseCase.class);
  private final CompatibilityScoreResponseListener listener =
      new CompatibilityScoreResponseListener(applyScore);

  @Test
  void applies_the_score_from_the_message() {
    MatchId matchId = MatchId.newId();
    CompatibilityScoreResponseMessage message =
        new CompatibilityScoreResponseMessage(matchId.toString(), "v0", 0.61, 40);

    listener.on(message);

    verify(applyScore).execute(matchId, 0.61, 40);
  }

  @Test
  void does_not_propagate_when_the_match_no_longer_exists() {
    MatchId matchId = MatchId.newId();
    CompatibilityScoreResponseMessage message =
        new CompatibilityScoreResponseMessage(matchId.toString(), "v0", 0.61, 40);
    doThrow(new MatchNotFoundException(matchId)).when(applyScore).execute(matchId, 0.61, 40);

    listener.on(message); // must not throw — a bad message shouldn't loop forever on requeue
  }
}
