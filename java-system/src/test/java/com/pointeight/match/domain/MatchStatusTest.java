package com.pointeight.match.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The transition table, verified exhaustively. */
class MatchStatusTest {

  @Test
  @DisplayName("PENDING can only be activated or rejected")
  void pendingTransitions() {
    assertThat(MatchStatus.PENDING.allowedTransitions())
        .containsExactlyInAnyOrder(MatchStatus.ACTIVE, MatchStatus.REJECTED);
  }

  @Test
  @DisplayName("ACTIVE can only expire or be rejected")
  void activeTransitions() {
    assertThat(MatchStatus.ACTIVE.allowedTransitions())
        .containsExactlyInAnyOrder(MatchStatus.EXPIRED, MatchStatus.REJECTED);
  }

  @Test
  @DisplayName("EXPIRED and REJECTED are terminal")
  void terminalStates() {
    assertThat(MatchStatus.EXPIRED.isTerminal()).isTrue();
    assertThat(MatchStatus.REJECTED.isTerminal()).isTrue();
    assertThat(MatchStatus.EXPIRED.allowedTransitions()).isEmpty();
    assertThat(MatchStatus.REJECTED.allowedTransitions()).isEmpty();
  }

  @Test
  @DisplayName("PENDING and ACTIVE are not terminal")
  void nonTerminalStates() {
    assertThat(MatchStatus.PENDING.isTerminal()).isFalse();
    assertThat(MatchStatus.ACTIVE.isTerminal()).isFalse();
  }

  @Test
  @DisplayName("no state can transition to itself")
  void noSelfTransitions() {
    for (MatchStatus status : MatchStatus.values()) {
      assertThat(status.canTransitionTo(status)).as("%s -> %s", status, status).isFalse();
    }
  }

  @Test
  @DisplayName("PENDING can't be reached again from any state")
  void pendingIsUnreachable() {
    for (MatchStatus status : MatchStatus.values()) {
      assertThat(status.canTransitionTo(MatchStatus.PENDING)).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(MatchStatus.class)
  @DisplayName("a null transition is never valid")
  void nullIsNeverAllowed(MatchStatus status) {
    assertThat(status.canTransitionTo(null)).isFalse();
  }
}
