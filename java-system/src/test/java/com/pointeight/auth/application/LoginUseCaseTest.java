package com.pointeight.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.HashedPassword;
import com.pointeight.auth.domain.InvalidCredentialsException;
import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.auth.domain.RefreshTokenRepository;
import com.pointeight.auth.domain.TokenIssuer;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginUseCaseTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final long REFRESH_TOKEN_TTL_DAYS = 30;

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
  private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
  private final LoginUseCase useCase =
      new LoginUseCase(
          accounts, passwordEncoder, tokenIssuer, refreshTokens, CLOCK, REFRESH_TOKEN_TTL_DAYS);

  @Test
  void issues_an_access_token_and_persists_a_refresh_token_for_correct_credentials() {
    Email email = new Email("marco@example.com");
    UserId userId = UserId.newId();
    Account account =
        Account.rehydrate(userId, email, new HashedPassword("hash"), Instant.now(), null);
    when(accounts.findByEmail(email)).thenReturn(Optional.of(account));
    when(passwordEncoder.matches("hunter2hunter2", "hash")).thenReturn(true);
    when(tokenIssuer.issue(userId)).thenReturn("signed-jwt");

    TokenPair tokens = useCase.execute(email, "hunter2hunter2");

    assertThat(tokens.accessToken()).isEqualTo("signed-jwt");
    assertThat(tokens.refreshToken()).isNotBlank();
    verify(refreshTokens)
        .save(argThat(token -> token.userId().equals(userId) && !token.isExpired(CLOCK)));
  }

  @Test
  void the_returned_refresh_token_matches_the_persisted_hash() {
    Email email = new Email("marco@example.com");
    UserId userId = UserId.newId();
    Account account =
        Account.rehydrate(userId, email, new HashedPassword("hash"), Instant.now(), null);
    when(accounts.findByEmail(email)).thenReturn(Optional.of(account));
    when(passwordEncoder.matches("hunter2hunter2", "hash")).thenReturn(true);

    TokenPair tokens = useCase.execute(email, "hunter2hunter2");

    verify(refreshTokens)
        .save(argThat(token -> token.tokenHash().equals(RefreshToken.hash(tokens.refreshToken()))));
  }

  @Test
  void rejects_an_unknown_email_without_leaking_that_its_unknown() {
    Email email = new Email("nobody@example.com");
    when(accounts.findByEmail(email)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(email, "whatever1"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(tokenIssuer, never()).issue(any());
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void rejects_the_wrong_password() {
    Email email = new Email("marco@example.com");
    Account account =
        Account.rehydrate(UserId.newId(), email, new HashedPassword("hash"), Instant.now(), null);
    when(accounts.findByEmail(email)).thenReturn(Optional.of(account));
    when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(email, "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(tokenIssuer, never()).issue(any());
    verify(refreshTokens, never()).save(any());
  }
}
