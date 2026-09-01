package com.pointeight.match.domain;

import com.pointeight.shared.domain.DomainException;

/** A transition was attempted that the state machine doesn't allow. Translated to HTTP 409. */
public class IllegalMatchTransitionException extends DomainException {

  private final MatchStatus from;
  private final MatchStatus to;

  public IllegalMatchTransitionException(MatchId id, MatchStatus from, MatchStatus to) {
    super("Match %s cannot go from %s to %s".formatted(id, from, to));
    this.from = from;
    this.to = to;
  }

  public MatchStatus from() {
    return from;
  }

  public MatchStatus to() {
    return to;
  }
}
