package com.pointeight.user.domain;

import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.event.UserRegisteredEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root of the user. Pure POJO: knows nothing about JPA, Spring, or HTTP.
 *
 * <p>Keeps the doc's two layers separate: {@link Profile} is visible, {@link
 * SimulationParameters} never leaves the System.
 */
public class User {

  private final UserId id;
  private Profile profile;
  private SimulationParameters simulationParameters;
  private ConfidenceScore cumulativeConfidenceScore;
  private final Instant createdAt;
  private Instant updatedAt;

  private final List<DomainEvent> pendingEvents = new ArrayList<>();

  private User(
      UserId id,
      Profile profile,
      SimulationParameters simulationParameters,
      ConfidenceScore cumulativeConfidenceScore,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.profile = Objects.requireNonNull(profile, "profile");
    this.simulationParameters =
        Objects.requireNonNull(simulationParameters, "simulationParameters");
    this.cumulativeConfidenceScore =
        Objects.requireNonNull(cumulativeConfidenceScore, "cumulativeConfidenceScore");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /**
   * Registers a new user. If Layer 2 parameters aren't provided, the System derives them from the
   * profile — the user never chooses them.
   */
  public static User register(Profile profile, SimulationParameters parameters, Clock clock) {
    Objects.requireNonNull(profile, "profile");
    Instant now = Instant.now(clock);
    User user =
        new User(
            UserId.newId(),
            profile,
            parameters == null ? TraitDerivation.defaultsFor(profile) : parameters,
            ConfidenceScore.initial(),
            now,
            now);
    user.pendingEvents.add(new UserRegisteredEvent(user.id, now));
    return user;
  }

  /** Rehydration from persistence. Only used by the adapter's mapper. */
  public static User rehydrate(
      UserId id,
      Profile profile,
      SimulationParameters simulationParameters,
      ConfidenceScore cumulativeConfidenceScore,
      Instant createdAt,
      Instant updatedAt) {
    return new User(
        id, profile, simulationParameters, cumulativeConfidenceScore, createdAt, updatedAt);
  }

  /** Only Layer 1 is editable by the user. */
  public void updateProfile(Profile newProfile, Clock clock) {
    this.profile = Objects.requireNonNull(newProfile, "profile");
    this.updatedAt = Instant.now(clock);
  }

  /** Layer 2 is recalibrated by the System, never by the user. */
  public void recalibrate(SimulationParameters newParameters, Clock clock) {
    this.simulationParameters = Objects.requireNonNull(newParameters, "simulationParameters");
    this.updatedAt = Instant.now(clock);
  }

  public void adjustConfidence(ConfidenceScore score, Clock clock) {
    this.cumulativeConfidenceScore = Objects.requireNonNull(score, "score");
    this.updatedAt = Instant.now(clock);
  }

  /** Returns the accumulated events and clears the buffer. Idempotent on successive calls. */
  public List<DomainEvent> pullEvents() {
    List<DomainEvent> drained = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return drained;
  }

  public UserId id() {
    return id;
  }

  public Profile profile() {
    return profile;
  }

  public SimulationParameters simulationParameters() {
    return simulationParameters;
  }

  public ConfidenceScore cumulativeConfidenceScore() {
    return cumulativeConfidenceScore;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  /** Identity by id, as befits an entity. */
  @Override
  public boolean equals(Object other) {
    return other instanceof User user && id.equals(user.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /** Deliberately without Layer 2: logs don't leak it either. */
  @Override
  public String toString() {
    return "User[id=%s, city=%s, age=%d]".formatted(id, profile.city(), profile.age());
  }
}
