package com.pointeight.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent representation of {@code Account}. Keyed by {@code user_id} directly — no surrogate
 * id, since this is a one-to-one detail of a user, not an aggregate of its own.
 */
@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AccountJpaEntity() {
    // Required by JPA.
  }

  AccountJpaEntity(UUID userId) {
    this.userId = userId;
  }

  UUID getUserId() {
    return userId;
  }

  String getEmail() {
    return email;
  }

  void setEmail(String email) {
    this.email = email;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
