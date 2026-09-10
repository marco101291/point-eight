package com.pointeight.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent representation of {@code RefreshToken}. Keyed by the hash itself, not a surrogate id
 * — a lookup by hash is the only access pattern this table ever needs (see {@code
 * RefreshTokenRepository}).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

  @Id
  @Column(name = "token_hash", nullable = false, updatable = false)
  private String tokenHash;

  @Column(name = "family_id", nullable = false, updatable = false)
  private String familyId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /**
   * Set only by {@code RefreshTokenJpaRepository#claim}'s bulk update, never through a setter
   * call followed by {@code save} — see the note on that method.
   */
  @Column(name = "used_at")
  private Instant usedAt;

  protected RefreshTokenJpaEntity() {
    // Required by JPA.
  }

  RefreshTokenJpaEntity(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  String getTokenHash() {
    return tokenHash;
  }

  String getFamilyId() {
    return familyId;
  }

  void setFamilyId(String familyId) {
    this.familyId = familyId;
  }

  UUID getUserId() {
    return userId;
  }

  void setUserId(UUID userId) {
    this.userId = userId;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  Instant getUsedAt() {
    return usedAt;
  }

  void setUsedAt(Instant usedAt) {
    this.usedAt = usedAt;
  }
}
