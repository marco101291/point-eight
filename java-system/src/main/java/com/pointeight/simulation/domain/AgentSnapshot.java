package com.pointeight.simulation.domain;

import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SimulationParameters;
import java.util.Objects;

/**
 * What the System sends the Engine about a user: both layers together. This is the only place
 * where {@link SimulationParameters} (Layer 2) leaves the System — never in an HTTP response to an
 * external client.
 */
public record AgentSnapshot(Profile profile, SimulationParameters simulationParameters) {

  public AgentSnapshot {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(simulationParameters, "simulationParameters");
  }
}
