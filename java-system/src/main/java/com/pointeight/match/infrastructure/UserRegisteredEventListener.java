package com.pointeight.match.infrastructure;

import com.pointeight.match.application.AssignNextMatchUseCase;
import com.pointeight.user.domain.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A brand-new user never got a first match otherwise: {@link AssignNextMatchUseCase} only ever
 * fired reactively off {@code MatchExpiredEvent} ({@link MatchExpiredEventListener}), which needs
 * an <em>existing</em> match to react to — a user with zero matches has nothing to expire, so
 * nothing ever assigned them one. Reacting to registration itself here closes that gap the same
 * way rematches already work, rather than a periodic sweep having to discover "who's never had
 * one" after the fact.
 *
 * <p>{@code AFTER_COMMIT}, same reasoning as {@code MatchExpiredEventListener}: fires only once
 * registration (both {@code User} and {@code Account}, {@code RegisterAccountUseCase}) has
 * actually committed, in {@code AssignNextMatchUseCase}'s own {@code REQUIRES_NEW} transaction. A
 * failure here doesn't undo the registration that already happened — the two are independent
 * facts, same as everywhere else this pattern is used.
 */
@Component
public class UserRegisteredEventListener {

  private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventListener.class);

  private final AssignNextMatchUseCase assignNextMatch;

  public UserRegisteredEventListener(AssignNextMatchUseCase assignNextMatch) {
    this.assignNextMatch = assignNextMatch;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(UserRegisteredEvent event) {
    try {
      assignNextMatch.execute(event.userId());
    } catch (RuntimeException e) {
      log.warn("Could not assign a first match for user {}: {}", event.userId(), e.getMessage());
    }
  }
}
