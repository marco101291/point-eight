package com.pointeight.match.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida del match. El patrón State expresado como enum: cada constante declara a qué
 * estados puede moverse, así que la máquina de estados completa se lee de un vistazo y no hay forma
 * de agregar una transición sin tocar esta tabla.
 *
 * <pre>
 *   PENDING ──activate──> ACTIVE ──expire──> EXPIRED (terminal)
 *      │                    │
 *      └──reject──> REJECTED <──reject──┘   (terminal)
 * </pre>
 */
public enum MatchStatus {

  /** Asignado por el Sistema, todavía no arrancó. */
  PENDING,

  /** Corriendo: la pareja está junta hasta que expire el reloj. */
  ACTIVE,

  /** Llegó a término. El Sistema asigna el siguiente match (automático desde M4). */
  EXPIRED,

  /** Cortado antes de término. */
  REJECTED;

  private static final Set<MatchStatus> NONE = Collections.unmodifiableSet(EnumSet.noneOf(MatchStatus.class));

  private Set<MatchStatus> allowed;

  static {
    PENDING.allowed = Collections.unmodifiableSet(EnumSet.of(ACTIVE, REJECTED));
    ACTIVE.allowed = Collections.unmodifiableSet(EnumSet.of(EXPIRED, REJECTED));
    EXPIRED.allowed = NONE;
    REJECTED.allowed = NONE;
  }

  /** Estados a los que se puede pasar desde este. */
  public Set<MatchStatus> allowedTransitions() {
    return allowed;
  }

  public boolean canTransitionTo(MatchStatus next) {
    return next != null && allowed.contains(next);
  }

  /** Un estado terminal no admite ninguna transición saliente. */
  public boolean isTerminal() {
    return allowed.isEmpty();
  }
}
