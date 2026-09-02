package com.pointeight.simulation.infrastructure;

import com.pointeight.match.domain.MatchId;
import com.pointeight.simulation.domain.AgentSnapshot;
import com.pointeight.simulation.domain.CompatibilityEngineException;
import com.pointeight.simulation.domain.CompatibilityEnginePort;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget adapter: publishes to {@code compatibility.score.requested} and returns. The
 * response arrives later, asynchronously, on {@link CompatibilityScoreResponseListener}.
 */
@Component
public class AmqpCompatibilityEngineClient implements CompatibilityEnginePort {

  private static final String MODEL_VERSION = "v0";

  private final RabbitTemplate rabbitTemplate;

  public AmqpCompatibilityEngineClient(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void requestAssessment(MatchId matchId, AgentSnapshot agentA, AgentSnapshot agentB) {
    CompatibilityScoreRequestMessage message =
        new CompatibilityScoreRequestMessage(
            matchId.toString(), MODEL_VERSION, AgentDto.from(agentA), AgentDto.from(agentB));

    try {
      rabbitTemplate.convertAndSend(
          CompatibilityMessagingConfig.EXCHANGE,
          CompatibilityMessagingConfig.REQUEST_ROUTING_KEY,
          message);
    } catch (AmqpException e) {
      throw new CompatibilityEngineException(
          "Could not publish the score request: " + e.getMessage(), e);
    }
  }
}
