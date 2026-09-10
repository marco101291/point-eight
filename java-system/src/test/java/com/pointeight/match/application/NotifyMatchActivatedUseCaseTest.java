package com.pointeight.match.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.HashedPassword;
import com.pointeight.push.domain.PushNotificationSender;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotifyMatchActivatedUseCaseTest {

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final PushNotificationSender pushNotifications = mock(PushNotificationSender.class);
  private final NotifyMatchActivatedUseCase useCase =
      new NotifyMatchActivatedUseCase(accounts, pushNotifications);

  @Test
  void sends_to_the_registered_token() {
    UserId userId = UserId.newId();
    Account account =
        Account.register(
            userId, new Email("marco@example.com"), new HashedPassword("hash"), Clock.systemUTC());
    account.registerPushToken("ExponentPushToken[abc]");
    when(accounts.findByUserId(userId)).thenReturn(Optional.of(account));

    useCase.execute(userId);

    verify(pushNotifications).send("ExponentPushToken[abc]", "0.8", "El Sistema decidió algo.");
  }

  @Test
  void does_nothing_when_the_user_never_registered_a_token() {
    UserId userId = UserId.newId();
    Account account =
        Account.register(
            userId, new Email("marco@example.com"), new HashedPassword("hash"), Clock.systemUTC());
    when(accounts.findByUserId(userId)).thenReturn(Optional.of(account));

    useCase.execute(userId);

    verify(pushNotifications, never()).send(any(), any(), any());
  }

  @Test
  void does_nothing_when_the_account_no_longer_exists() {
    UserId userId = UserId.newId();
    when(accounts.findByUserId(userId)).thenReturn(Optional.empty());

    useCase.execute(userId);

    verify(pushNotifications, never()).send(any(), any(), any());
  }
}
