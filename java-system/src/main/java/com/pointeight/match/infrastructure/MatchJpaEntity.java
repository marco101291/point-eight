package com.pointeight.match.infrastructure;

import com.pointeight.match.domain.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persistent representation of the match. The {@code Match} aggregate doesn't know this class. */
@Entity
@Table(
    name = "matches",
    indexes = {
      @Index(name = "idx_matches_status", columnList = "status"),
      @Index(name = "idx_matches_user_a", columnList = "user_a_id"),
      @Index(name = "idx_matches_user_b", columnList = "user_b_id")
    })
public class MatchJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_a_id", nullable = false, updatable = false)
  private UUID userAId;

  @Column(name = "user_b_id", nullable = false, updatable = false)
  private UUID userBId;

  // Was `updatable = false` while the domain field was `final` — DEC-028 made it mutable
  // (ExpiryDurationPolicy can replace the default while a match is still PENDING), and Hibernate
  // silently drops a column marked updatable=false from every UPDATE statement, so that flag has
  // to go too; otherwise the domain change has no effect at all, no error either.
  @Column(name = "expiry_duration_seconds", nullable = false)
  private long expiryDurationSeconds;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private MatchStatus status;

  /** Null until the Engine calculates it (M2 onward). */
  @Column(name = "compatibility_score")
  private Double compatibilityScore;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  protected MatchJpaEntity() {
    // Required by JPA.
  }

  MatchJpaEntity(UUID id) {
    this.id = id;
  }

  UUID getId() {
    return id;
  }

  UUID getUserAId() {
    return userAId;
  }

  void setUserAId(UUID userAId) {
    this.userAId = userAId;
  }

  UUID getUserBId() {
    return userBId;
  }

  void setUserBId(UUID userBId) {
    this.userBId = userBId;
  }

  long getExpiryDurationSeconds() {
    return expiryDurationSeconds;
  }

  void setExpiryDurationSeconds(long expiryDurationSeconds) {
    this.expiryDurationSeconds = expiryDurationSeconds;
  }

  MatchStatus getStatus() {
    return status;
  }

  void setStatus(MatchStatus status) {
    this.status = status;
  }

  Double getCompatibilityScore() {
    return compatibilityScore;
  }

  void setCompatibilityScore(Double compatibilityScore) {
    this.compatibilityScore = compatibilityScore;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  Instant getActivatedAt() {
    return activatedAt;
  }

  void setActivatedAt(Instant activatedAt) {
    this.activatedAt = activatedAt;
  }

  Instant getEndedAt() {
    return endedAt;
  }

  void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }
}
