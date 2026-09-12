package com.pointeight.match.domain;

import com.pointeight.match.domain.event.MatchActivatedEvent;
import com.pointeight.match.domain.event.MatchAssignedEvent;
import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root of the match. Pure POJO: all lifecycle logic lives here and can be tested without
 * spinning up Spring or Postgres.
 *
 * <p>Domain events accumulate in {@code pendingEvents}; the use case drains them with {@link
 * #pullEvents()} after persisting, so nothing is published that the transaction ends up rolling
 * back.
 */
public class Match {

  private final MatchId id;
  private final UserId userAId;
  private final UserId userBId;
  private Duration expiryDuration;
  private MatchStatus status;
  private CompatibilityScore compatibilityScore;
  private final Instant createdAt;
  private Instant activatedAt;
  private Instant endedAt;

  private final List<DomainEvent> pendingEvents = new ArrayList<>();

  private Match(
      MatchId id,
      UserId userAId,
      UserId userBId,
      Duration expiryDuration,
      MatchStatus status,
      CompatibilityScore compatibilityScore,
      Instant createdAt,
      Instant activatedAt,
      Instant endedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userAId = Objects.requireNonNull(userAId, "userAId");
    this.userBId = Objects.requireNonNull(userBId, "userBId");
    this.expiryDuration = Objects.requireNonNull(expiryDuration, "expiryDuration");
    this.status = Objects.requireNonNull(status, "status");
    this.compatibilityScore = compatibilityScore;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.activatedAt = activatedAt;
    this.endedAt = endedAt;
  }

  /**
   * The System proposes a match. Born in {@link MatchStatus#PENDING} and records the assignment
   * event.
   */
  public static Match propose(
      UserId userAId, UserId userBId, Duration expiryDuration, Clock clock) {
    Objects.requireNonNull(userAId, "userAId");
    Objects.requireNonNull(userBId, "userBId");
    Objects.requireNonNull(expiryDuration, "expiryDuration");
    if (userAId.equals(userBId)) {
      throw new IllegalArgumentException("A user cannot be matched with themselves");
    }
    if (expiryDuration.isZero() || expiryDuration.isNegative()) {
      throw new IllegalArgumentException(
          "expiryDuration must be positive, got " + expiryDuration);
    }

    Instant now = Instant.now(clock);
    Match match =
        new Match(
            MatchId.newId(),
            userAId,
            userBId,
            expiryDuration,
            MatchStatus.PENDING,
            null,
            now,
            null,
            null);
    match.pendingEvents.add(
        new MatchAssignedEvent(match.id, userAId, userBId, expiryDuration, now));
    return match;
  }

  /** Rehydration from persistence. Only used by the adapter's mapper; emits no events. */
  public static Match rehydrate(
      MatchId id,
      UserId userAId,
      UserId userBId,
      Duration expiryDuration,
      MatchStatus status,
      CompatibilityScore compatibilityScore,
      Instant createdAt,
      Instant activatedAt,
      Instant endedAt) {
    return new Match(
        id,
        userAId,
        userBId,
        expiryDuration,
        status,
        compatibilityScore,
        createdAt,
        activatedAt,
        endedAt);
  }

  // --- Transiciones -------------------------------------------------------

  public void activate(Clock clock) {
    transitionTo(MatchStatus.ACTIVE);
    this.activatedAt = Instant.now(clock);
    this.pendingEvents.add(new MatchActivatedEvent(id, userAId, userBId, this.activatedAt));
  }

  public void expire(Clock clock) {
    transitionTo(MatchStatus.EXPIRED);
    this.endedAt = Instant.now(clock);
    this.pendingEvents.add(new MatchExpiredEvent(id, userAId, userBId, this.endedAt));
  }

  public void reject(Clock clock) {
    transitionTo(MatchStatus.REJECTED);
    this.endedAt = Instant.now(clock);
  }

  private void transitionTo(MatchStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new IllegalMatchTransitionException(id, status, next);
    }
    this.status = next;
  }

  /** The score comes from the Engine (M2 onward) and only makes sense before the match ends. */
  public void assignCompatibilityScore(CompatibilityScore score) {
    Objects.requireNonNull(score, "score");
    if (status.isTerminal()) {
      throw new IllegalStateException(
          "Cannot score match %s: it's already %s".formatted(id, status));
    }
    this.compatibilityScore = score;
  }

  /**
   * Replaces the default duration assigned at {@link #propose} with one derived from the Engine's
   * compatibility assessment ({@code ExpiryDurationPolicy}, DEC-028). Only while {@code PENDING}:
   * once activated, {@link #expiresAt} is already anchored to {@code activatedAt} plus whatever
   * duration was in effect at that moment, so changing it afterward would silently move a
   * countdown someone might already be watching.
   */
  public void applyExpiryDuration(Duration newDuration) {
    Objects.requireNonNull(newDuration, "newDuration");
    if (newDuration.isZero() || newDuration.isNegative()) {
      throw new IllegalArgumentException("newDuration must be positive, got " + newDuration);
    }
    if (status != MatchStatus.PENDING) {
      throw new IllegalStateException(
          "Cannot change the expiry duration for match %s: it's already %s".formatted(id, status));
    }
    this.expiryDuration = newDuration;
  }

  // --- Queries --------------------------------------------------------------

  /** When the match is due. Empty while it hasn't been activated. */
  public Optional<Instant> expiresAt() {
    return Optional.ofNullable(activatedAt).map(start -> start.plus(expiryDuration));
  }

  /** Whether it's past its due date while active. Checked by the scheduler from M4 on. */
  public boolean isDue(Clock clock) {
    return status == MatchStatus.ACTIVE
        && expiresAt().map(at -> !Instant.now(clock).isBefore(at)).orElse(false);
  }

  public boolean involves(UserId userId) {
    return userAId.equals(userId) || userBId.equals(userId);
  }

  /** Returns the accumulated events and clears the buffer. Idempotent on successive calls. */
  public List<DomainEvent> pullEvents() {
    List<DomainEvent> drained = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return drained;
  }

  public MatchId id() {
    return id;
  }

  public UserId userAId() {
    return userAId;
  }

  public UserId userBId() {
    return userBId;
  }

  public Duration expiryDuration() {
    return expiryDuration;
  }

  public MatchStatus status() {
    return status;
  }

  public Optional<CompatibilityScore> compatibilityScore() {
    return Optional.ofNullable(compatibilityScore);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> activatedAt() {
    return Optional.ofNullable(activatedAt);
  }

  public Optional<Instant> endedAt() {
    return Optional.ofNullable(endedAt);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Match match && id.equals(match.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Match[id=%s, status=%s, a=%s, b=%s]".formatted(id, status, userAId, userBId);
  }
}
