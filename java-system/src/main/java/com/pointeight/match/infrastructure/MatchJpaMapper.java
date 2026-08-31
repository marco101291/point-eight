package com.pointeight.match.infrastructure;

import com.pointeight.match.domain.CompatibilityScore;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.user.domain.UserId;
import java.time.Duration;

final class MatchJpaMapper {

  private MatchJpaMapper() {}

  static MatchJpaEntity toEntity(Match match, MatchJpaEntity target) {
    MatchJpaEntity entity = target == null ? new MatchJpaEntity(match.id().value()) : target;
    entity.setUserAId(match.userAId().value());
    entity.setUserBId(match.userBId().value());
    entity.setExpiryDurationSeconds(match.expiryDuration().toSeconds());
    entity.setStatus(match.status());
    entity.setCompatibilityScore(
        match.compatibilityScore().map(CompatibilityScore::value).orElse(null));
    entity.setCreatedAt(match.createdAt());
    entity.setActivatedAt(match.activatedAt().orElse(null));
    entity.setEndedAt(match.endedAt().orElse(null));
    return entity;
  }

  static Match toDomain(MatchJpaEntity entity) {
    return Match.rehydrate(
        new MatchId(entity.getId()),
        new UserId(entity.getUserAId()),
        new UserId(entity.getUserBId()),
        Duration.ofSeconds(entity.getExpiryDurationSeconds()),
        entity.getStatus(),
        entity.getCompatibilityScore() == null
            ? null
            : CompatibilityScore.of(entity.getCompatibilityScore()),
        entity.getCreatedAt(),
        entity.getActivatedAt(),
        entity.getEndedAt());
  }
}
