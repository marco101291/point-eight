package com.pointeight.simulation.infrastructure;

import com.pointeight.user.domain.CommunicationProfile;

record CommunicationProfileDto(
    double criticism, double contempt, double defensiveness, double stonewalling) {

  static CommunicationProfileDto from(CommunicationProfile profile) {
    return new CommunicationProfileDto(
        profile.criticism(), profile.contempt(), profile.defensiveness(), profile.stonewalling());
  }
}
