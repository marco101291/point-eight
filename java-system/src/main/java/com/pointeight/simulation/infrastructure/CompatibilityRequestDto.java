package com.pointeight.simulation.infrastructure;

/** Envelope with {@code modelVersion}: lets the contract be versioned once M3 changes the model. */
record CompatibilityRequestDto(String modelVersion, AgentDto agentA, AgentDto agentB) {}
