package com.pointeight.auth.infrastructure;

import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.auth.domain.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Adapter that implements the domain port on top of Spring Data JPA. */
@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

  private final RefreshTokenJpaRepository jpa;

  public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public RefreshToken save(RefreshToken token) {
    return RefreshTokenJpaMapper.toDomain(jpa.save(RefreshTokenJpaMapper.toEntity(token)));
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    return jpa.findById(tokenHash).map(RefreshTokenJpaMapper::toDomain);
  }

  @Override
  public boolean claim(String tokenHash, Instant usedAt) {
    return jpa.claim(tokenHash, usedAt) > 0;
  }

  @Override
  public void deleteFamily(String familyId) {
    jpa.deleteByFamilyId(familyId);
  }
}
