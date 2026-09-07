package com.pointeight.events;

import com.pointeight.match.domain.event.MatchAssignedEvent;
import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserRepository;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records the handful of domain events M5's live feed knows how to render, so the admin panel can
 * poll for "what's happened since I last asked" instead of needing a persistent connection.
 *
 * <p>Deliberately not every {@link DomainEvent} type: events this doesn't recognize are silently
 * ignored (the {@code default} branch below) rather than stored as an unreadable blob — a
 * generic "something happened" row would be useless to the panel anyway.
 *
 * <p>In-memory and lost on restart, on purpose: this is an observability feed for one admin panel,
 * not an audit log — nothing here needs to survive a redeploy.
 */
@Component
public class RecentEventsFeed {

  private static final int CAPACITY = 200;

  private final UserRepository users;
  private final Deque<RecentEvent> buffer = new ConcurrentLinkedDeque<>();
  private long sequence = 0;

  public RecentEventsFeed(UserRepository users) {
    this.users = users;
  }

  /**
   * {@code AFTER_COMMIT}, not a plain listener: {@code MatchAssignedEvent}/{@code
   * MatchExpiredEvent} are published from inside {@code @Transactional} use cases
   * (CreateManualMatchUseCase, MatchLifecycleUseCases), and a feed the panel presents as "what
   * actually happened" shouldn't show something whose transaction later rolls back — the same
   * reasoning {@code MatchExpiredEventListener} already applies to the same event type
   * (DEC-015), reused here instead of copying {@code DomainEventLogger}'s plain-listener shape.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(DomainEvent event) {
    // Sequence 0 is a placeholder — record() assigns the real one under a lock, see its javadoc.
    RecentEvent draft =
        switch (event) {
          case MatchAssignedEvent e ->
              new RecentEvent(
                  0,
                  "MatchAssignedEvent",
                  e.matchId().toString(),
                  summarize(e.userAId()),
                  summarize(e.userBId()),
                  e.expiryDuration().toSeconds(),
                  e.occurredAt());
          case MatchExpiredEvent e ->
              new RecentEvent(
                  0,
                  "MatchExpiredEvent",
                  e.matchId().toString(),
                  summarize(e.userAId()),
                  summarize(e.userBId()),
                  null,
                  e.occurredAt());
          default -> null;
        };

    if (draft != null) {
      record(draft);
    }
  }

  /**
   * Assigns the next sequence number and appends, both under the same lock — so two events
   * published from different threads close together can't land in the buffer out of increasing
   * sequence order (the panel assumes ascending order to track what it's already seen), and the
   * capacity check-then-evict can't transiently let the buffer grow past {@link #CAPACITY}. Only
   * this bookkeeping is synchronized, not {@link #summarize}'s repository lookups above, so a slow
   * lookup on one thread doesn't block another's.
   */
  private synchronized void record(RecentEvent draft) {
    RecentEvent recorded =
        new RecentEvent(
            ++sequence,
            draft.type(),
            draft.matchId(),
            draft.userA(),
            draft.userB(),
            draft.expiryDurationSeconds(),
            draft.occurredAt());
    buffer.addLast(recorded);
    while (buffer.size() > CAPACITY) {
      buffer.pollFirst();
    }
  }

  /**
   * Falls back to a null city/profession rather than dropping the event: a user who was deleted
   * between the event firing and now shouldn't erase the fact that it happened.
   */
  private UserSummary summarize(UserId id) {
    return users
        .findById(id)
        .map(u -> new UserSummary(id.toString(), u.profile().city(), u.profile().profession()))
        .orElseGet(() -> new UserSummary(id.toString(), null, null));
  }

  /** Every recorded event with a sequence number greater than {@code since}, oldest first. */
  public List<RecentEvent> since(long since) {
    return buffer.stream().filter(e -> e.sequence() > since).toList();
  }
}
