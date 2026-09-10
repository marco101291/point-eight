package com.pointeight.auth.domain;

import java.time.Instant;
import java.util.Optional;

/** Outbound port of {@link RefreshToken}. No Spring or JPA types. */
public interface RefreshTokenRepository {

  RefreshToken save(RefreshToken token);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Atomically marks a token used, but only if it wasn't already — the single mechanism behind
   * both of {@code RefreshAccessTokenUseCase}'s guarantees: two concurrent refreshes racing on the
   * same token can't both "win" (only one claim succeeds), and presenting an old, already-rotated
   * token gets refused rather than silently accepted (its claim always fails, since it was already
   * claimed the first time it rotated). Returns whether this call is the one that won.
   */
  boolean claim(String tokenHash, Instant usedAt);

  /**
   * Deletes every token that ever descended from the same login. Used on logout (end the session
   * cleanly, including any already-used tombstones) and on detected reuse (can't tell the attacker
   * from the legitimate holder, so both lose their session and have to log in again).
   */
  void deleteFamily(String familyId);
}
