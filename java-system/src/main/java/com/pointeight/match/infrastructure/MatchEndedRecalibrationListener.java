package com.pointeight.match.infrastructure;

import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.match.domain.event.MatchRejectedEvent;
import com.pointeight.user.application.RecordObservedMatchUseCase;
import com.pointeight.user.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DEC-026's recalibration trigger: once per match, at its terminal transition — {@code EXPIRED},
 * or {@code REJECTED} only if it had gone {@code ACTIVE} first. A rejection straight from {@code
 * PENDING} observed nothing about either user, so it doesn't count.
 *
 * <p>A separate listener from {@link MatchExpiredEventListener} rather than a second job inside
 * it: rematching and recalibrating are independent reactions to the same fact, and either one
 * failing shouldn't stop the other. Same {@code AFTER_COMMIT} reasoning as that class.
 */
@Component
public class MatchEndedRecalibrationListener {

  private static final Logger log = LoggerFactory.getLogger(MatchEndedRecalibrationListener.class);

  private final RecordObservedMatchUseCase recordObservedMatch;

  public MatchEndedRecalibrationListener(RecordObservedMatchUseCase recordObservedMatch) {
    this.recordObservedMatch = recordObservedMatch;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(MatchExpiredEvent event) {
    recalibrateBoth(event.userAId(), event.userBId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(MatchRejectedEvent event) {
    if (event.wasActive()) {
      recalibrateBoth(event.userAId(), event.userBId());
    }
  }

  private void recalibrateBoth(UserId userAId, UserId userBId) {
    tryRecalibrate(userAId);
    tryRecalibrate(userBId);
  }

  private void tryRecalibrate(UserId userId) {
    try {
      recordObservedMatch.execute(userId);
    } catch (RuntimeException e) {
      log.warn("Could not recalibrate confidence for user {}: {}", userId, e.getMessage());
    }
  }
}
