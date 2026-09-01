package com.pointeight.simulation.infrastructure;

import com.pointeight.simulation.domain.AgentSnapshot;

record AgentDto(ProfileDto profile, SimulationParametersDto simulationParameters) {

  static AgentDto from(AgentSnapshot snapshot) {
    return new AgentDto(
        ProfileDto.from(snapshot.profile()),
        SimulationParametersDto.from(snapshot.simulationParameters()));
  }
}
