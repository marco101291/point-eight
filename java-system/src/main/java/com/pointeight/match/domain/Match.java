package com.pointeight.match.domain;

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
 * Aggregate root del match. POJO puro: toda la lógica del ciclo de vida vive acá y se puede testear
 * sin levantar Spring ni Postgres.
 *
 * <p>Los eventos de dominio se acumulan en {@code pendingEvents}; el caso de uso los drena con
 * {@link #pullEvents()} después de persistir, para no publicar nada que la transacción vaya a
 * revertir.
 */
public class Match {

  private final MatchId id;
  private final UserId userAId;
  private final UserId userBId;
  private final Duration expiryDuration;
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
   * El Sistema propone un match. Nace en {@link MatchStatus#PENDING} y registra el evento de
   * asignación.
   */
  public static Match propose(
      UserId userAId, UserId userBId, Duration expiryDuration, Clock clock) {
    Objects.requireNonNull(userAId, "userAId");
    Objects.requireNonNull(userBId, "userBId");
    Objects.requireNonNull(expiryDuration, "expiryDuration");
    if (userAId.equals(userBId)) {
      throw new IllegalArgumentException("Un usuario no puede ser matcheado consigo mismo");
    }
    if (expiryDuration.isZero() || expiryDuration.isNegative()) {
      throw new IllegalArgumentException(
          "expiryDuration debe ser positiva, llegó " + expiryDuration);
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

  /** Rehidratación desde persistencia. Sólo la usa el mapper del adapter; no emite eventos. */
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

  /** El score llega del Motor (M2 en adelante) y sólo tiene sentido antes de que el match termine. */
  public void assignCompatibilityScore(CompatibilityScore score) {
    Objects.requireNonNull(score, "score");
    if (status.isTerminal()) {
      throw new IllegalStateException(
          "No se puede puntuar el match %s: ya está en %s".formatted(id, status));
    }
    this.compatibilityScore = score;
  }

  // --- Consultas ----------------------------------------------------------

  /** Momento en que el match vence. Vacío mientras no se haya activado. */
  public Optional<Instant> expiresAt() {
    return Optional.ofNullable(activatedAt).map(start -> start.plus(expiryDuration));
  }

  /** Si ya pasó su vencimiento estando activo. En M4 lo consulta el scheduler. */
  public boolean isDue(Clock clock) {
    return status == MatchStatus.ACTIVE
        && expiresAt().map(at -> !Instant.now(clock).isBefore(at)).orElse(false);
  }

  public boolean involves(UserId userId) {
    return userAId.equals(userId) || userBId.equals(userId);
  }

  /** Devuelve los eventos acumulados y limpia el buffer. Idempotente en llamadas sucesivas. */
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
