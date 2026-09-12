package com.pointeight.match.infrastructure;

import com.pointeight.match.application.RequestCompatibilityScoreUseCase;
import com.pointeight.match.domain.event.MatchAssignedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Scoring used to be entirely on-demand — nothing called {@link RequestCompatibilityScoreUseCase}
 * except the manual {@code /score} endpoint, even though its own javadoc had already named {@code
 * MatchAssignedEvent} as an intended trigger. Wiring it up here (DEC-028) is what makes
 * score-driven match duration (see {@code ApplyCompatibilityScoreUseCase}) actually happen for
 * real matches, not just ones an operator happens to score by hand.
 *
 * <p>{@code AFTER_COMMIT}, same reasoning as every other listener reacting to a match event: the
 * match has to actually be there before asking the Engine about it.
 */
@Component
public class MatchAssignedEventListener {

  private static final Logger log = LoggerFactory.getLogger(MatchAssignedEventListener.class);

  private final RequestCompatibilityScoreUseCase requestScore;

  public MatchAssignedEventListener(RequestCompatibilityScoreUseCase requestScore) {
    this.requestScore = requestScore;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(MatchAssignedEvent event) {
    try {
      requestScore.execute(event.matchId());
    } catch (RuntimeException e) {
      log.warn("Could not request a compatibility score for match {}: {}",
          event.matchId(), e.getMessage());
    }
  }
}
