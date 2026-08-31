package com.pointeight.match.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** La tabla de transiciones, verificada de forma exhaustiva. */
class MatchStatusTest {

  @Test
  @DisplayName("PENDING sólo puede activarse o rechazarse")
  void pendingTransitions() {
    assertThat(MatchStatus.PENDING.allowedTransitions())
        .containsExactlyInAnyOrder(MatchStatus.ACTIVE, MatchStatus.REJECTED);
  }

  @Test
  @DisplayName("ACTIVE sólo puede expirar o rechazarse")
  void activeTransitions() {
    assertThat(MatchStatus.ACTIVE.allowedTransitions())
        .containsExactlyInAnyOrder(MatchStatus.EXPIRED, MatchStatus.REJECTED);
  }

  @Test
  @DisplayName("EXPIRED y REJECTED son terminales")
  void terminalStates() {
    assertThat(MatchStatus.EXPIRED.isTerminal()).isTrue();
    assertThat(MatchStatus.REJECTED.isTerminal()).isTrue();
    assertThat(MatchStatus.EXPIRED.allowedTransitions()).isEmpty();
    assertThat(MatchStatus.REJECTED.allowedTransitions()).isEmpty();
  }

  @Test
  @DisplayName("PENDING y ACTIVE no son terminales")
  void nonTerminalStates() {
    assertThat(MatchStatus.PENDING.isTerminal()).isFalse();
    assertThat(MatchStatus.ACTIVE.isTerminal()).isFalse();
  }

  @Test
  @DisplayName("ningún estado puede transicionar hacia sí mismo")
  void noSelfTransitions() {
    for (MatchStatus status : MatchStatus.values()) {
      assertThat(status.canTransitionTo(status)).as("%s -> %s", status, status).isFalse();
    }
  }

  @Test
  @DisplayName("no se puede volver a PENDING desde ningún estado")
  void pendingIsUnreachable() {
    for (MatchStatus status : MatchStatus.values()) {
      assertThat(status.canTransitionTo(MatchStatus.PENDING)).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(MatchStatus.class)
  @DisplayName("una transición nula nunca es válida")
  void nullIsNeverAllowed(MatchStatus status) {
    assertThat(status.canTransitionTo(null)).isFalse();
  }
}
