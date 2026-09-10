package com.pointeight.auth.application;

import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.auth.domain.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole reason DEC-023 exists: unlike the access token (a self-validating JWT that no one can
 * revoke before it expires), the refresh token is a real database row, so logging out can actually
 * delete it — the whole family, not just the presented token, so no already-rotated tombstone from
 * this session is left sitting around. Silently succeeds when the token is already gone (already
 * logged out, already expired) — the caller's goal ("this session shouldn't work anymore") is
 * already true either way, so there's nothing to report as an error.
 *
 * <p>Safe to keep {@code @Transactional} here, unlike {@link RefreshAccessTokenUseCase}: nothing
 * in this method throws after the delete, so there's no exception to roll the transaction back.
 */
@Service
public class LogoutUseCase {

  private final RefreshTokenRepository refreshTokens;

  public LogoutUseCase(RefreshTokenRepository refreshTokens) {
    this.refreshTokens = refreshTokens;
  }

  @Transactional
  public void execute(String rawRefreshToken) {
    refreshTokens
        .findByTokenHash(RefreshToken.hash(rawRefreshToken))
        .ifPresent(token -> refreshTokens.deleteFamily(token.familyId()));
  }
}
