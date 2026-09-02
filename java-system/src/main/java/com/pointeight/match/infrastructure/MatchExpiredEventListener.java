package com.pointeight.match.infrastructure;

import com.pointeight.match.application.AssignNextMatchUseCase;
import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.user.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The System reassigning unilaterally, for real: when a match expires, both people get searched
 * for a next candidate without anyone asking for it.
 *
 * <p>Runs {@code AFTER_COMMIT}, not on a plain {@code @EventListener}: it fires only once {@code
 * expire()}'s transaction has actually committed, and in its own transaction (see {@code
 * AssignNextMatchUseCase}'s {@code @Transactional}). If finding a next candidate fails for one or
 * both users, the expiry that already happened stays expired — the two are independent facts.
 */
@Component
public class MatchExpiredEventListener {

  private static final Logger log = LoggerFactory.getLogger(MatchExpiredEventListener.class);

  private final AssignNextMatchUseCase assignNextMatch;

  public MatchExpiredEventListener(AssignNextMatchUseCase assignNextMatch) {
    this.assignNextMatch = assignNextMatch;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(MatchExpiredEvent event) {
    tryAssignNextMatch(event.userAId());
    tryAssignNextMatch(event.userBId());
  }

  private void tryAssignNextMatch(UserId userId) {
    try {
      assignNextMatch.execute(userId);
    } catch (RuntimeException e) {
      log.warn("Could not assign a next match for user {}: {}", userId, e.getMessage());
    }
  }
}
