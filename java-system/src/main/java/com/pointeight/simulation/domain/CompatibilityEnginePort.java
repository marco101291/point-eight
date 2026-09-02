package com.pointeight.simulation.domain;

import com.pointeight.match.domain.MatchId;

/**
 * Outbound port to the Engine. Fire-and-forget, not request/response: M2/M3 had this return a
 * {@link CompatibilityAssessment} synchronously over REST, but that meant Java stayed blocked for
 * as long as the Engine's batch took (~0.85s, and growing with the simulation count) — exactly
 * the problem M4's RabbitMQ exists to remove. {@code matchId} rides along so the response, arriving
 * later over its own queue, can be matched back to the match that asked for it.
 */
public interface CompatibilityEnginePort {

  void requestAssessment(MatchId matchId, AgentSnapshot agentA, AgentSnapshot agentB);
}
