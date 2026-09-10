package com.pointeight.auth.application;

import com.pointeight.auth.domain.InvalidRefreshTokenException;
import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.auth.domain.RefreshTokenRepository;
import com.pointeight.auth.domain.TokenIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Trades a refresh token for a new access token — the whole point of DEC-023's split: the mobile
 * client calls this instead of asking the user to log in again every time the short-lived access
 * token expires.
 *
 * <p>Rotates on every use via an atomic claim ({@link RefreshTokenRepository#claim}), not a
 * find-then-delete-then-insert from application code: that would leave a window where two
 * concurrent refreshes on the same token could both read it as live before either wrote anything,
 * letting both "win." A failed claim means this exact token was already used once — either this
 * call lost that race against a concurrent refresh, or someone replayed an old, already-rotated
 * token. The two are indistinguishable from here, so both are treated as theft: the whole family
 * is revoked, not just this one token, forcing every descendant of it back to a real login.
 *
 * <p>Deliberately **not** {@code @Transactional} at the method level. It was, briefly, wrapping
 * {@code claim} + revoke-on-failure + throw as one transaction — and that combination deadlocks:
 * on a failed claim, revoking the family needs to delete the very row {@code claim} just examined,
 * but the surrounding transaction is still open (it can't commit until this method returns), so
 * the revoke either blocks forever on a lock its own uncommitted parent holds, or — worse, if
 * "fixed" by running the revoke in its own {@code REQUIRES_NEW} transaction — deadlocks outright:
 * the suspended parent holds a lock the child needs, and the parent can't resume (to release it)
 * until the child returns. Leaving this method unannotated means each repository call below
 * commits on its own (Spring Data wraps every repository method in its own transaction already),
 * so a revoked family is never undone by an exception thrown after it. The cost: {@code claim} and
 * the final {@code save} are no longer atomic with each other — if `save` failed right after a
 * successful claim, the old token would be burned with nothing to replace it. Accepted: that forces
 * an unnecessary re-login, not a security hole, and is far rarer than the deadlock it replaces.
 */
@Service
public class RefreshAccessTokenUseCase {

  private final RefreshTokenRepository refreshTokens;
  private final TokenIssuer tokenIssuer;
  private final Clock clock;
  private final Duration refreshTokenTtl;

  public RefreshAccessTokenUseCase(
      RefreshTokenRepository refreshTokens,
      TokenIssuer tokenIssuer,
      Clock clock,
      @Value("${pointeight.auth.refresh-token-ttl-days}") long refreshTokenTtlDays) {
    this.refreshTokens = refreshTokens;
    this.tokenIssuer = tokenIssuer;
    this.clock = clock;
    this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
  }

  public TokenPair execute(String rawRefreshToken) {
    String hash = RefreshToken.hash(rawRefreshToken);
    RefreshToken existing =
        refreshTokens.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

    if (existing.isExpired(clock)) {
      throw new InvalidRefreshTokenException();
    }

    boolean claimed = refreshTokens.claim(hash, Instant.now(clock));
    if (!claimed) {
      refreshTokens.deleteFamily(existing.familyId());
      throw new InvalidRefreshTokenException();
    }

    String accessToken = tokenIssuer.issue(existing.userId());
    RefreshToken.Issued issued =
        RefreshToken.issueInFamily(existing.familyId(), existing.userId(), refreshTokenTtl, clock);
    refreshTokens.save(issued.token());
    return new TokenPair(accessToken, issued.rawValue());
  }
}
