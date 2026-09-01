package com.pointeight.match.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Match lifecycle. The State pattern expressed as an enum: each constant declares which states it
 * can move to, so the whole state machine reads at a glance and no transition can be added without
 * touching this table.
 *
 * <pre>
 *   PENDING ──activate──> ACTIVE ──expire──> EXPIRED (terminal)
 *      │                    │
 *      └──reject──> REJECTED <──reject──┘   (terminal)
 * </pre>
 */
public enum MatchStatus {

  /** Assigned by the System, hasn't started yet. */
  PENDING,

  /** Running: the couple is together until the clock expires. */
  ACTIVE,

  /** Ran its course. The System assigns the next match (automatic from M4). */
  EXPIRED,

  /** Cut short before running its course. */
  REJECTED;

  private static final Set<MatchStatus> NONE = Collections.unmodifiableSet(EnumSet.noneOf(MatchStatus.class));

  private Set<MatchStatus> allowed;

  static {
    PENDING.allowed = Collections.unmodifiableSet(EnumSet.of(ACTIVE, REJECTED));
    ACTIVE.allowed = Collections.unmodifiableSet(EnumSet.of(EXPIRED, REJECTED));
    EXPIRED.allowed = NONE;
    REJECTED.allowed = NONE;
  }

  /** States this one can move to. */
  public Set<MatchStatus> allowedTransitions() {
    return allowed;
  }

  public boolean canTransitionTo(MatchStatus next) {
    return next != null && allowed.contains(next);
  }

  /** A terminal state doesn't allow any outgoing transition. */
  public boolean isTerminal() {
    return allowed.isEmpty();
  }
}
