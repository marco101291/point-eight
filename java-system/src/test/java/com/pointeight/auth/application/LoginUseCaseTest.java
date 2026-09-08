package com.pointeight.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.HashedPassword;
import com.pointeight.auth.domain.InvalidCredentialsException;
import com.pointeight.auth.domain.TokenIssuer;
import com.pointeight.user.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginUseCaseTest {

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
  private final LoginUseCase useCase = new LoginUseCase(accounts, passwordEncoder, tokenIssuer);

  @Test
  void issues_a_token_for_correct_credentials() {
    Email email = new Email("marco@example.com");
    UserId userId = UserId.newId();
    Account account = Account.rehydrate(userId, email, new HashedPassword("hash"), Instant.now());
    when(accounts.findByEmail(email)).thenReturn(Optional.of(account));
    when(passwordEncoder.matches("hunter2hunter2", "hash")).thenReturn(true);
    when(tokenIssuer.issue(userId)).thenReturn("signed-jwt");

    String token = useCase.execute(email, "hunter2hunter2");

    assertThat(token).isEqualTo("signed-jwt");
  }

  @Test
  void rejects_an_unknown_email_without_leaking_that_its_unknown() {
    Email email = new Email("nobody@example.com");
    when(accounts.findByEmail(email)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(email, "whatever1"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(tokenIssuer, never()).issue(any());
  }

  @Test
  void rejects_the_wrong_password() {
    Email email = new Email("marco@example.com");
    Account account =
        Account.rehydrate(UserId.newId(), email, new HashedPassword("hash"), Instant.now());
    when(accounts.findByEmail(email)).thenReturn(Optional.of(account));
    when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(email, "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(tokenIssuer, never()).issue(any());
  }
}
