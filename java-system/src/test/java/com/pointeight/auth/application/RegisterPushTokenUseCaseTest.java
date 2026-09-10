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
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegisterPushTokenUseCaseTest {

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final RegisterPushTokenUseCase useCase = new RegisterPushTokenUseCase(accounts);

  @Test
  void registers_the_token_on_the_caller_s_own_account() {
    UserId userId = UserId.newId();
    Account account =
        Account.register(
            userId, new Email("marco@example.com"), new HashedPassword("hash"), Clock.systemUTC());
    when(accounts.findByUserId(userId)).thenReturn(Optional.of(account));

    useCase.execute(userId, "ExponentPushToken[abc]");

    assertThat(account.pushToken()).contains("ExponentPushToken[abc]");
    verify(accounts).save(account);
  }

  @Test
  void a_second_registration_replaces_the_first() {
    UserId userId = UserId.newId();
    Account account =
        Account.register(
            userId, new Email("marco@example.com"), new HashedPassword("hash"), Clock.systemUTC());
    account.registerPushToken("ExponentPushToken[old]");
    when(accounts.findByUserId(userId)).thenReturn(Optional.of(account));

    useCase.execute(userId, "ExponentPushToken[new]");

    assertThat(account.pushToken()).contains("ExponentPushToken[new]");
  }

  @Test
  void an_unknown_user_fails_loudly_instead_of_silently_no_opping() {
    UserId userId = UserId.newId();
    when(accounts.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId, "ExponentPushToken[abc]"))
        .isInstanceOf(IllegalStateException.class);
    verify(accounts, never()).save(any());
  }
}
