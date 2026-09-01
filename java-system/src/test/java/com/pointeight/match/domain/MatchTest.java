package com.pointeight.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pointeight.match.domain.event.MatchAssignedEvent;
import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The match's full lifecycle, without Spring and without a database. */
class MatchTest {

  private static final Instant T0 = Instant.parse("2026-08-29T12:00:00Z");
  private static final Duration TWELVE_HOURS = Duration.ofHours(12);

  private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
  private final UserId alice = UserId.newId();
  private final UserId bob = UserId.newId();

  private Match pendingMatch() {
    return Match.propose(alice, bob, TWELVE_HOURS, clock);
  }

  @Nested
  @DisplayName("when proposed")
  class Proposal {

    @Test
    void nace_en_PENDING_sin_score() {
      Match match = pendingMatch();

      assertThat(match.status()).isEqualTo(MatchStatus.PENDING);
      assertThat(match.compatibilityScore()).isEmpty();
      assertThat(match.createdAt()).isEqualTo(T0);
      assertThat(match.activatedAt()).isEmpty();
      assertThat(match.endedAt()).isEmpty();
    }

    @Test
    void registra_MatchAssignedEvent() {
      Match match = pendingMatch();

      assertThat(match.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              MatchAssignedEvent.class,
              event -> {
                assertThat(event.userAId()).isEqualTo(alice);
                assertThat(event.userBId()).isEqualTo(bob);
                assertThat(event.expiryDuration()).isEqualTo(TWELVE_HOURS);
                assertThat(event.occurredAt()).isEqualTo(T0);
              });
    }

    @Test
    void rechaza_matchear_a_alguien_consigo_mismo() {
      assertThatThrownBy(() -> Match.propose(alice, alice, TWELVE_HOURS, clock))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("matched with themselves");
    }

    @Test
    void rechaza_duracion_cero_o_negativa() {
      assertThatThrownBy(() -> Match.propose(alice, bob, Duration.ZERO, clock))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> Match.propose(alice, bob, Duration.ofHours(-1), clock))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("legal transitions")
  class LegalTransitions {

    @Test
    void PENDING_a_ACTIVE() {
      Match match = pendingMatch();
      match.activate(clock);

      assertThat(match.status()).isEqualTo(MatchStatus.ACTIVE);
      assertThat(match.activatedAt()).contains(T0);
      assertThat(match.expiresAt()).contains(T0.plus(TWELVE_HOURS));
    }

    @Test
    void ACTIVE_a_EXPIRED_emite_MatchExpiredEvent() {
      Match match = pendingMatch();
      match.activate(clock);
      match.pullEvents(); // discard the assignment event

      match.expire(clock);

      assertThat(match.status()).isEqualTo(MatchStatus.EXPIRED);
      assertThat(match.endedAt()).contains(T0);
      assertThat(match.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              MatchExpiredEvent.class,
              event -> {
                assertThat(event.userAId()).isEqualTo(alice);
                assertThat(event.userBId()).isEqualTo(bob);
              });
    }

    @Test
    void PENDING_a_REJECTED() {
      Match match = pendingMatch();
      match.reject(clock);

      assertThat(match.status()).isEqualTo(MatchStatus.REJECTED);
      assertThat(match.endedAt()).contains(T0);
    }

    @Test
    void ACTIVE_a_REJECTED() {
      Match match = pendingMatch();
      match.activate(clock);
      match.reject(clock);

      assertThat(match.status()).isEqualTo(MatchStatus.REJECTED);
    }

    @Test
    void rechazar_no_emite_evento_de_expiracion() {
      Match match = pendingMatch();
      match.pullEvents();
      match.reject(clock);

      assertThat(match.pullEvents()).isEmpty();
    }
  }

  @Nested
  @DisplayName("illegal transitions")
  class IllegalTransitions {

    @Test
    void no_se_puede_expirar_un_match_PENDING() {
      Match match = pendingMatch();

      assertThatThrownBy(() -> match.expire(clock))
          .isInstanceOf(IllegalMatchTransitionException.class);
      assertThat(match.status()).as("state doesn't change").isEqualTo(MatchStatus.PENDING);
    }

    @Test
    void no_se_puede_activar_dos_veces() {
      Match match = pendingMatch();
      match.activate(clock);

      assertThatThrownBy(() -> match.activate(clock))
          .isInstanceOf(IllegalMatchTransitionException.class)
          .satisfies(
              e -> {
                var ex = (IllegalMatchTransitionException) e;
                assertThat(ex.from()).isEqualTo(MatchStatus.ACTIVE);
                assertThat(ex.to()).isEqualTo(MatchStatus.ACTIVE);
              });
    }

    @Test
    void un_match_EXPIRED_no_admite_nada() {
      Match match = pendingMatch();
      match.activate(clock);
      match.expire(clock);

      assertThatThrownBy(() -> match.activate(clock))
          .isInstanceOf(IllegalMatchTransitionException.class);
      assertThatThrownBy(() -> match.reject(clock))
          .isInstanceOf(IllegalMatchTransitionException.class);
      assertThatThrownBy(() -> match.expire(clock))
          .isInstanceOf(IllegalMatchTransitionException.class);
    }

    @Test
    void un_match_REJECTED_no_admite_nada() {
      Match match = pendingMatch();
      match.reject(clock);

      assertThatThrownBy(() -> match.activate(clock))
          .isInstanceOf(IllegalMatchTransitionException.class);
      assertThatThrownBy(() -> match.expire(clock))
          .isInstanceOf(IllegalMatchTransitionException.class);
    }
  }

  @Nested
  @DisplayName("compatibility score")
  class Scoring {

    @Test
    void se_puede_asignar_mientras_el_match_sigue_vivo() {
      Match match = pendingMatch();
      match.assignCompatibilityScore(CompatibilityScore.of(0.8));

      assertThat(match.compatibilityScore()).contains(CompatibilityScore.of(0.8));
    }

    @Test
    void no_se_puede_puntuar_un_match_terminado() {
      Match match = pendingMatch();
      match.reject(clock);

      assertThatThrownBy(() -> match.assignCompatibilityScore(CompatibilityScore.of(0.5)))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("expiry")
  class Expiry {

    @Test
    void un_match_sin_activar_no_tiene_fecha_de_vencimiento() {
      assertThat(pendingMatch().expiresAt()).isEmpty();
      assertThat(pendingMatch().isDue(clock)).isFalse();
    }

    @Test
    void isDue_es_falso_antes_del_vencimiento_y_verdadero_despues() {
      Match match = pendingMatch();
      match.activate(clock);

      Clock justBefore = Clock.fixed(T0.plus(TWELVE_HOURS).minusSeconds(1), ZoneOffset.UTC);
      Clock exactly = Clock.fixed(T0.plus(TWELVE_HOURS), ZoneOffset.UTC);

      assertThat(match.isDue(justBefore)).isFalse();
      assertThat(match.isDue(exactly)).as("the exact edge is already due").isTrue();
    }

    @Test
    void un_match_expirado_ya_no_esta_vencido_pendiente_de_procesar() {
      Match match = pendingMatch();
      match.activate(clock);
      match.expire(clock);

      Clock later = Clock.fixed(T0.plus(Duration.ofDays(2)), ZoneOffset.UTC);
      assertThat(match.isDue(later)).isFalse();
    }
  }

  @Test
  void pullEvents_vacia_el_buffer() {
    Match match = pendingMatch();

    assertThat(match.pullEvents()).hasSize(1);
    assertThat(match.pullEvents()).as("second call").isEmpty();
  }

  @Test
  void involves_reconoce_a_ambos_lados_y_solo_a_ellos() {
    Match match = pendingMatch();

    assertThat(match.involves(alice)).isTrue();
    assertThat(match.involves(bob)).isTrue();
    assertThat(match.involves(UserId.newId())).isFalse();
  }
}
