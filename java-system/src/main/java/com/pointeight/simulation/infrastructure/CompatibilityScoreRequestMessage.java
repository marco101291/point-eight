package com.pointeight.simulation.infrastructure;

/** Published to {@code compatibility.score.requested}. Java -> Engine, fire-and-forget. */
record CompatibilityScoreRequestMessage(
    String matchId, String modelVersion, AgentDto agentA, AgentDto agentB) {}
