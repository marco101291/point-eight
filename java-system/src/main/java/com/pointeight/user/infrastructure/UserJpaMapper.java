package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.CommunicationProfile;
import com.pointeight.user.domain.ConfidenceScore;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SimulationParameters;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;

/**
 * Translates between the pure aggregate and its JPA representation. The only place that knows
 * both sides.
 */
final class UserJpaMapper {

  private UserJpaMapper() {}

  static UserJpaEntity toEntity(User user, UserJpaEntity target) {
    UserJpaEntity entity = target == null ? new UserJpaEntity(user.id().value()) : target;

    Profile profile = user.profile();
    entity.setAge(profile.age());
    entity.setGender(profile.gender());
    entity.setSeekingGenders(profile.seekingGenders());
    entity.setSeekingType(profile.seekingType());
    entity.setCity(profile.city());
    entity.setProfession(profile.profession());
    entity.setHobbies(profile.hobbies());
    entity.setPhotoUrl(profile.photoUrl());

    SimulationParameters params = user.simulationParameters();
    entity.setAttachmentStyle(params.attachmentStyle());
    entity.setAttachmentIntensity(params.attachmentIntensity());
    entity.setCriticism(params.communicationProfile().criticism());
    entity.setContempt(params.communicationProfile().contempt());
    entity.setDefensiveness(params.communicationProfile().defensiveness());
    entity.setStonewalling(params.communicationProfile().stonewalling());
    entity.setInfidelityHistory(params.infidelityHistory());
    entity.setRelationshipHistory(params.relationshipHistory());
    entity.setActiveAddiction(params.activeAddiction());
    entity.setStressBaseline(params.stressBaseline());
    entity.setCommitmentPaceExpectation(params.commitmentPaceExpectation());

    entity.setCumulativeConfidenceScore(user.cumulativeConfidenceScore().value());
    entity.setCreatedAt(user.createdAt());
    entity.setUpdatedAt(user.updatedAt());
    return entity;
  }

  static User toDomain(UserJpaEntity entity) {
    Profile profile =
        new Profile(
            entity.getAge(),
            entity.getGender(),
            entity.getSeekingGenders(),
            entity.getSeekingType(),
            entity.getCity(),
            entity.getProfession(),
            entity.getHobbies(),
            entity.getPhotoUrl());

    SimulationParameters params =
        new SimulationParameters(
            entity.getAttachmentStyle(),
            entity.getAttachmentIntensity(),
            new CommunicationProfile(
                entity.getCriticism(),
                entity.getContempt(),
                entity.getDefensiveness(),
                entity.getStonewalling()),
            entity.isInfidelityHistory(),
            entity.getRelationshipHistory(),
            entity.isActiveAddiction(),
            entity.getStressBaseline(),
            entity.getCommitmentPaceExpectation());

    return User.rehydrate(
        new UserId(entity.getId()),
        profile,
        params,
        new ConfidenceScore(entity.getCumulativeConfidenceScore()),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
