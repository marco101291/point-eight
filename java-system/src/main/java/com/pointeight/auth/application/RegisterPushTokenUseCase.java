package com.pointeight.auth.application;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.user.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets the mobile client tell the System which device to push to (M7). Called right after login,
 * and again on every launch while already authenticated — Expo push tokens can change (a
 * reinstall, restoring on a new device), so this has to be safe to call repeatedly, not just once
 * at registration. See {@link Account#registerPushToken} for why a new call simply replaces the
 * old token rather than this project modeling multiple devices per account.
 */
@Service
public class RegisterPushTokenUseCase {

  private final AccountRepository accounts;

  public RegisterPushTokenUseCase(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Transactional
  public void execute(UserId userId, String pushToken) {
    Account account =
        accounts
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("No account for user " + userId));
    account.registerPushToken(pushToken);
    accounts.save(account);
  }
}
