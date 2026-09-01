package com.pointeight.simulation.infrastructure;

import com.pointeight.user.domain.AttachmentStyle;
import com.pointeight.user.domain.SimulationParameters;

/**
 * Layer 2 as the Engine expects it. This record and {@link ProfileDto} are the only place in the
 * System where both layers coexist outside the {@code User} aggregate.
 */
record SimulationParametersDto(
    AttachmentStyle attachmentStyle,
    double attachmentIntensity,
    CommunicationProfileDto communicationProfile,
    boolean infidelityHistory,
    int relationshipHistory,
    boolean activeAddiction,
    double stressBaseline,
    double commitmentPaceExpectation) {

  static SimulationParametersDto from(SimulationParameters parameters) {
    return new SimulationParametersDto(
        parameters.attachmentStyle(),
        parameters.attachmentIntensity(),
        CommunicationProfileDto.from(parameters.communicationProfile()),
        parameters.infidelityHistory(),
        parameters.relationshipHistory(),
        parameters.activeAddiction(),
        parameters.stressBaseline(),
        parameters.commitmentPaceExpectation());
  }
}
