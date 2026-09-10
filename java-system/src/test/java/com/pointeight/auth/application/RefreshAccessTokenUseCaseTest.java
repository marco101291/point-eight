package com.pointeight.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.auth.domain.InvalidRefreshTokenException;
import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.auth.domain.RefreshTokenRepository;
import com.pointeight.auth.domain.TokenIssuer;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefreshAccessTokenUseCaseTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
  private static final long REFRESH_TOKEN_TTL_DAYS = 30;

  private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
  private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
  private final RefreshAccessTokenUseCase useCase =
      new RefreshAccessTokenUseCase(refreshTokens, tokenIssuer, CLOCK, REFRESH_TOKEN_TTL_DAYS);

  @Test
  void trades_a_valid_refresh_token_for_a_new_access_token_and_rotates_within_the_same_family() {
    UserId userId = UserId.newId();
    RefreshToken.Issued issued = RefreshToken.issueNewFamily(userId, Duration.ofDays(30), CLOCK);
    when(refreshTokens.findByTokenHash(issued.token().tokenHash()))
        .thenReturn(Optional.of(issued.token()));
    when(refreshTokens.claim(issued.token().tokenHash(), T0)).thenReturn(true);
    when(tokenIssuer.issue(userId)).thenReturn("new-access-token");

    TokenPair result = useCase.execute(issued.rawValue());

    assertThat(result.accessToken()).isEqualTo("new-access-token");
    assertThat(result.refreshToken()).isNotEqualTo(issued.rawValue());
    verify(refreshTokens)
        .save(
            argThat(
                t ->
                    t.userId().equals(userId)
                        && t.familyId().equals(issued.token().familyId())));
    verify(refreshTokens, never()).deleteFamily(any());
  }

  @Test
  void an_unknown_token_is_rejected() {
    when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute("not-a-real-token"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(tokenIssuer, never()).issue(any());
    verify(refreshTokens, never()).claim(any(), any());
  }

  @Test
  void an_expired_token_is_rejected_without_touching_the_rest_of_the_family() {
    UserId userId = UserId.newId();
    String raw = "raw-refresh-token";
    RefreshToken expired =
        RefreshToken.rehydrate(RefreshToken.hash(raw), "some-family", userId, T0.minusSeconds(1), T0, null);
    when(refreshTokens.findByTokenHash(RefreshToken.hash(raw))).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> useCase.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokens, never()).claim(any(), any());
    verify(refreshTokens, never()).deleteFamily(any());
    verify(tokenIssuer, never()).issue(any());
  }

  @Test
  void losing_the_claim_race_revokes_the_whole_family_instead_of_silently_rotating() {
    // Simulates two near-simultaneous /refresh calls on the same token: whichever transaction's
    // UPDATE commits first wins the claim, the other's affects zero rows. This test is the
    // loser's perspective.
    UserId userId = UserId.newId();
    RefreshToken.Issued issued = RefreshToken.issueNewFamily(userId, Duration.ofDays(30), CLOCK);
    when(refreshTokens.findByTokenHash(issued.token().tokenHash()))
        .thenReturn(Optional.of(issued.token()));
    when(refreshTokens.claim(issued.token().tokenHash(), T0)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(issued.rawValue()))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokens).deleteFamily(issued.token().familyId());
    verify(tokenIssuer, never()).issue(any());
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void replaying_an_old_already_rotated_token_also_revokes_the_family() {
    // Same code path as the race above, different real-world cause: a genuinely old token,
    // rotated away from minutes ago, presented again — its claim fails just the same, since
    // claim() only succeeds once per token, ever.
    UserId userId = UserId.newId();
    String raw = "stale-refresh-token";
    RefreshToken stale =
        RefreshToken.rehydrate(
            RefreshToken.hash(raw), "family-under-attack", userId, T0.plus(Duration.ofDays(1)), T0, null);
    when(refreshTokens.findByTokenHash(RefreshToken.hash(raw))).thenReturn(Optional.of(stale));
    when(refreshTokens.claim(eq(RefreshToken.hash(raw)), any())).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokens).deleteFamily("family-under-attack");
  }
}
