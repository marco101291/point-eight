package com.pointeight.user.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root del usuario. POJO puro: no conoce JPA, Spring ni HTTP.
 *
 * <p>Mantiene separadas las dos capas del doc: {@link Profile} es visible, {@link
 * SimulationParameters} no sale nunca del Sistema.
 */
public class User {

  private final UserId id;
  private Profile profile;
  private SimulationParameters simulationParameters;
  private ConfidenceScore cumulativeConfidenceScore;
  private final Instant createdAt;
  private Instant updatedAt;

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
   * Alta de un usuario nuevo. Si no se aportan parámetros de Capa 2, el Sistema los deriva del
   * perfil — el usuario nunca los elige.
   */
  public static User register(Profile profile, SimulationParameters parameters, Clock clock) {
    Objects.requireNonNull(profile, "profile");
    Instant now = Instant.now(clock);
    return new User(
        UserId.newId(),
        profile,
        parameters == null ? TraitDerivation.defaultsFor(profile) : parameters,
        ConfidenceScore.initial(),
        now,
        now);
  }

  /** Rehidratación desde persistencia. Sólo la usa el mapper del adapter. */
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

  /** Sólo la Capa 1 es editable por el usuario. */
  public void updateProfile(Profile newProfile, Clock clock) {
    this.profile = Objects.requireNonNull(newProfile, "profile");
    this.updatedAt = Instant.now(clock);
  }

  /** La Capa 2 la reajusta el Sistema, nunca el usuario. */
  public void recalibrate(SimulationParameters newParameters, Clock clock) {
    this.simulationParameters = Objects.requireNonNull(newParameters, "simulationParameters");
    this.updatedAt = Instant.now(clock);
  }

  public void adjustConfidence(ConfidenceScore score, Clock clock) {
    this.cumulativeConfidenceScore = Objects.requireNonNull(score, "score");
    this.updatedAt = Instant.now(clock);
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

  /** Identidad por id, como corresponde a una entidad. */
  @Override
  public boolean equals(Object other) {
    return other instanceof User user && id.equals(user.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /** Deliberadamente sin Capa 2: los logs tampoco la filtran. */
  @Override
  public String toString() {
    return "User[id=%s, city=%s, age=%d]".formatted(id, profile.city(), profile.age());
  }
}
