package com.pointeight.auth.infrastructure;

import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.user.domain.UserId;

/** Translates between the pure domain type and its JPA representation. */
final class RefreshTokenJpaMapper {

  private RefreshTokenJpaMapper() {}

  static RefreshTokenJpaEntity toEntity(RefreshToken token) {
    RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(token.tokenHash());
    entity.setFamilyId(token.familyId());
    entity.setUserId(token.userId().value());
    entity.setExpiresAt(token.expiresAt());
    entity.setCreatedAt(token.createdAt());
    entity.setUsedAt(token.usedAt());
    return entity;
  }

  static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
    return RefreshToken.rehydrate(
        entity.getTokenHash(),
        entity.getFamilyId(),
        new UserId(entity.getUserId()),
        entity.getExpiresAt(),
        entity.getCreatedAt(),
        entity.getUsedAt());
  }
}
