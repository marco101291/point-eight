package com.pointeight.match.infrastructure;

import com.pointeight.match.application.NotifyMatchActivatedUseCase;
import com.pointeight.match.domain.event.MatchActivatedEvent;
import com.pointeight.user.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Push notifications for a match going live (M7) — the trigger point DEC-021's groundwork noted as
 * missing: {@code activate()} used to record no domain event at all.
 *
 * <p>{@code AFTER_COMMIT}, same reasoning as {@link
 * com.pointeight.match.application.AssignNextMatchUseCase}'s caller: fires only once {@code
 * activate()}'s transaction has actually committed, in its own transaction ({@code
 * NotifyMatchActivatedUseCase}'s {@code @Transactional}). If sending fails for one or both users,
 * the activation that already happened stays activated — the two are independent facts.
 */
@Component
public class MatchActivatedEventListener {

  private static final Logger log = LoggerFactory.getLogger(MatchActivatedEventListener.class);

  private final NotifyMatchActivatedUseCase notify;

  public MatchActivatedEventListener(NotifyMatchActivatedUseCase notify) {
    this.notify = notify;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(MatchActivatedEvent event) {
    tryNotify(event.userAId());
    tryNotify(event.userBId());
  }

  private void tryNotify(UserId userId) {
    try {
      notify.execute(userId);
    } catch (RuntimeException e) {
      log.warn("Could not send a push notification to user {}: {}", userId, e.getMessage());
    }
  }
}
