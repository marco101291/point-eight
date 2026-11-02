package com.pointeight.match.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.match.domain.event.MatchRejectedEvent;
import com.pointeight.user.application.RecordObservedMatchUseCase;
import com.pointeight.user.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MatchEndedRecalibrationListenerTest {

  private final RecordObservedMatchUseCase recordObservedMatch =
      mock(RecordObservedMatchUseCase.class);
  private final MatchEndedRecalibrationListener listener =
      new MatchEndedRecalibrationListener(recordObservedMatch);

  private final UserId userAId = UserId.newId();
  private final UserId userBId = UserId.newId();

  @Test
  void an_expired_match_recalibrates_both_users() {
    listener.on(new MatchExpiredEvent(MatchId.newId(), userAId, userBId, Instant.now()));

    verify(recordObservedMatch).execute(userAId);
    verify(recordObservedMatch).execute(userBId);
  }

  @Test
  void a_match_rejected_after_going_active_recalibrates_both_users() {
    listener.on(new MatchRejectedEvent(MatchId.newId(), userAId, userBId, true, Instant.now()));

    verify(recordObservedMatch).execute(userAId);
    verify(recordObservedMatch).execute(userBId);
  }

  @Test
  void a_match_rejected_while_still_pending_recalibrates_nobody() {
    listener.on(new MatchRejectedEvent(MatchId.newId(), userAId, userBId, false, Instant.now()));

    verifyNoInteractions(recordObservedMatch);
  }

  @Test
  void a_failure_for_one_user_does_not_stop_the_other_from_being_recalibrated() {
    when(recordObservedMatch.execute(userAId)).thenThrow(new RuntimeException("boom"));

    listener.on(new MatchExpiredEvent(MatchId.newId(), userAId, userBId, Instant.now()));

    verify(recordObservedMatch).execute(userBId);
  }
}
