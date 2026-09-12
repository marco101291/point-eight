package com.pointeight.simulation.infrastructure;

import com.pointeight.match.application.ApplyCompatibilityScoreUseCase;
import com.pointeight.match.domain.MatchId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code compatibility.score.computed}. Catches and logs instead of letting an exception
 * propagate: an uncaught exception here would NACK the message, and Spring AMQP's default is to
 * requeue it — a permanently bad message (unknown matchId, already-terminal match) would then
 * retry forever instead of just being dropped.
 */
@Component
public class CompatibilityScoreResponseListener {

  private static final Logger log = LoggerFactory.getLogger(CompatibilityScoreResponseListener.class);

  private final ApplyCompatibilityScoreUseCase applyScore;

  public CompatibilityScoreResponseListener(ApplyCompatibilityScoreUseCase applyScore) {
    this.applyScore = applyScore;
  }

  @RabbitListener(queues = CompatibilityMessagingConfig.RESPONSE_QUEUE)
  public void on(CompatibilityScoreResponseMessage message) {
    try {
      applyScore.execute(
          MatchId.of(message.matchId()), message.compatibilityScore(), message.expiryDays());
    } catch (RuntimeException e) {
      log.warn(
          "Could not apply the compatibility score for match {}: {}",
          message.matchId(),
          e.getMessage());
    }
  }
}
