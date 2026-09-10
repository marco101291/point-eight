package com.pointeight.match.application;

import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.push.domain.PushNotificationSender;
import com.pointeight.user.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends the push notification for a match going live (M7). Triggered by {@link
 * com.pointeight.match.infrastructure.MatchActivatedEventListener}, one call per user in the
 * newly-activated match. Silently does nothing if that user never registered a push token —
 * most accounts won't have (see {@code Account#registerPushToken}).
 *
 * <p>The notification says nothing about the match itself — no age, city, profession, and
 * certainly no name (there isn't one anywhere in this system) — just enough to prompt someone to
 * open the app. Anything more would leak Layer 1 onto a lock screen, which the reveal screen's own
 * gated flow (DEC-021) exists specifically to avoid.
 *
 * <p>{@code REQUIRES_NEW}, same reasoning {@code AssignNextMatchUseCase} already documents
 * (`DEC-015`): the listener that calls this runs {@code AFTER_COMMIT} of {@code activate()}'s
 * transaction, whose {@code EntityManager} is on its way out — the default propagation would
 * silently join that soon-to-be-discarded resource instead of opening a real one of its own.
 */
@Service
public class NotifyMatchActivatedUseCase {

  private final AccountRepository accounts;
  private final PushNotificationSender pushNotifications;

  public NotifyMatchActivatedUseCase(
      AccountRepository accounts, PushNotificationSender pushNotifications) {
    this.accounts = accounts;
    this.pushNotifications = pushNotifications;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(UserId userId) {
    accounts
        .findByUserId(userId)
        .flatMap(account -> account.pushToken())
        .ifPresent(token -> pushNotifications.send(token, "0.8", "El Sistema decidió algo."));
  }
}
