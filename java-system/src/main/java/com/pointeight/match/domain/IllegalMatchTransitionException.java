package com.pointeight.match.domain;

import com.pointeight.shared.domain.DomainException;

/** Se intentó una transición que la máquina de estados no permite. Se traduce a HTTP 409. */
public class IllegalMatchTransitionException extends DomainException {

  private final MatchStatus from;
  private final MatchStatus to;

  public IllegalMatchTransitionException(MatchId id, MatchStatus from, MatchStatus to) {
    super("El match %s no puede pasar de %s a %s".formatted(id, from, to));
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
