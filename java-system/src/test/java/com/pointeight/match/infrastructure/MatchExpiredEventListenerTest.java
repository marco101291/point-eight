package com.pointeight.match.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.match.application.AssignNextMatchUseCase;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.user.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MatchExpiredEventListenerTest {

  private final AssignNextMatchUseCase assignNextMatch = mock(AssignNextMatchUseCase.class);
  private final MatchExpiredEventListener listener = new MatchExpiredEventListener(assignNextMatch);

  @Test
  void tries_to_assign_a_next_match_for_both_users() {
    UserId userAId = UserId.newId();
    UserId userBId = UserId.newId();
    MatchExpiredEvent event =
        new MatchExpiredEvent(MatchId.newId(), userAId, userBId, Instant.now());

    listener.on(event);

    verify(assignNextMatch).execute(userAId);
    verify(assignNextMatch).execute(userBId);
  }

  @Test
  void a_failure_for_one_user_does_not_stop_the_other_from_being_tried() {
    UserId userAId = UserId.newId();
    UserId userBId = UserId.newId();
    MatchExpiredEvent event =
        new MatchExpiredEvent(MatchId.newId(), userAId, userBId, Instant.now());
    when(assignNextMatch.execute(userAId)).thenThrow(new RuntimeException("boom"));

    listener.on(event);

    verify(assignNextMatch).execute(userBId);
  }
}
