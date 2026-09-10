package com.pointeight.auth.domain;

import com.pointeight.user.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * The revocable half of the access/refresh split (DEC-023): a long-lived, opaque credential
 * exchanged for a new access token, so the JWT itself (self-validating, DEC-022) can stay
 * short-lived — bounding how long a "logged out" or stolen access token stays usable to minutes
 * instead of days. Opaque, not a JWT: nothing needs to read claims out of it locally, only look it
 * up by hash, so there's nothing to gain from making it self-describing and everything to lose if
 * a leaked database row let someone reconstruct a live token — hashed at rest for the same reason
 * {@link HashedPassword} never stores a raw password.
 *
 * <p>Every token belongs to a {@code familyId}: the chain of tokens descended from one login,
 * each one born when the previous rotated. That lineage is what makes reuse detection possible —
 * {@code RefreshAccessTokenUseCase} atomically claims a token before rotating it ({@code
 * RefreshTokenRepository#claim}), and a claim that fails (the token was already claimed) means
 * either a benign race between two near-simultaneous refreshes, or a stale token being replayed
 * after the real client already moved past it. The two look identical from here, so both get
 * treated as theft: the whole family is revoked, not just the one token, forcing a real login.
 */
public final class RefreshToken {

  private static final int SECRET_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final String tokenHash;
  private final String familyId;
  private final UserId userId;
  private final Instant expiresAt;
  private final Instant createdAt;
  private final Instant usedAt;

  private RefreshToken(
      String tokenHash,
      String familyId,
      UserId userId,
      Instant expiresAt,
      Instant createdAt,
      Instant usedAt) {
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    this.familyId = Objects.requireNonNull(familyId, "familyId");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.usedAt = usedAt;
  }

  /** Starts a brand new family — the root of a fresh rotation chain, issued at login. */
  public static Issued issueNewFamily(UserId userId, Duration ttl, Clock clock) {
    return issueInFamily(UUID.randomUUID().toString(), userId, ttl, clock);
  }

  /** Issues the next link in an existing family — used when rotating during a refresh. */
  public static Issued issueInFamily(String familyId, UserId userId, Duration ttl, Clock clock) {
    byte[] secret = new byte[SECRET_BYTES];
    RANDOM.nextBytes(secret);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    Instant now = Instant.now(clock);
    RefreshToken token = new RefreshToken(hash(raw), familyId, userId, now.plus(ttl), now, null);
    return new Issued(token, raw);
  }

  /** Rehydration from persistence. Only used by the adapter's mapper. */
  public static RefreshToken rehydrate(
      String tokenHash,
      String familyId,
      UserId userId,
      Instant expiresAt,
      Instant createdAt,
      Instant usedAt) {
    return new RefreshToken(tokenHash, familyId, userId, expiresAt, createdAt, usedAt);
  }

  /**
   * SHA-256 of a raw token, hex-encoded. Deterministic on purpose — it doubles as the lookup key,
   * so a presented refresh token is found by hashing it again, the same way {@code
   * PasswordEncoder.matches} re-derives instead of decrypting.
   */
  public static String hash(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public boolean isExpired(Clock clock) {
    return !Instant.now(clock).isBefore(expiresAt);
  }

  public String tokenHash() {
    return tokenHash;
  }

  public String familyId() {
    return familyId;
  }

  public UserId userId() {
    return userId;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /** Null until {@link RefreshTokenRepository#claim} marks it used, ahead of rotation. */
  public Instant usedAt() {
    return usedAt;
  }

  /** Identity by the hash — that's what a row in the table actually is. */
  @Override
  public boolean equals(Object other) {
    return other instanceof RefreshToken token && tokenHash.equals(token.tokenHash);
  }

  @Override
  public int hashCode() {
    return tokenHash.hashCode();
  }

  /** The record to persist, plus the one-time raw secret to hand back to the caller. */
  public record Issued(RefreshToken token, String rawValue) {}
}
