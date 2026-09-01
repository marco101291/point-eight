package com.pointeight.simulation.domain;

/**
 * Outbound port to the Engine. In M2 it's implemented by a synchronous REST adapter; in M4 it
 * coexists with AMQP. The domain doesn't know which.
 */
public interface CompatibilityEnginePort {

  CompatibilityAssessment assess(AgentSnapshot agentA, AgentSnapshot agentB);
}
