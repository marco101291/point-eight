package com.pointeight.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.simulation.domain.AgentSnapshot;
import com.pointeight.simulation.domain.CompatibilityEnginePort;
import com.pointeight.user.domain.AttachmentStyle;
import com.pointeight.user.domain.CommunicationProfile;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.SimulationParameters;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestCompatibilityScoreUseCaseTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  private MatchRepository matches;
  private UserRepository users;
  private CompatibilityEnginePort engine;
  private RequestCompatibilityScoreUseCase useCase;

  @BeforeEach
  void setUp() {
    matches = mock(MatchRepository.class);
    users = mock(UserRepository.class);
    engine = mock(CompatibilityEnginePort.class);
    useCase = new RequestCompatibilityScoreUseCase(matches, users, engine);
  }

  @Test
  void publica_el_pedido_de_score_y_devuelve_el_match_sin_tocar() {
    User userA = sampleUser();
    User userB = sampleUser();
    Match match = Match.propose(userA.id(), userB.id(), Duration.ofHours(12), CLOCK);

    when(matches.findById(match.id())).thenReturn(Optional.of(match));
    when(users.findById(userA.id())).thenReturn(Optional.of(userA));
    when(users.findById(userB.id())).thenReturn(Optional.of(userB));

    Match result = useCase.execute(match.id());

    assertThat(result).isSameAs(match);
    assertThat(result.compatibilityScore()).isEmpty();
    verify(engine)
        .requestAssessment(eq(match.id()), any(AgentSnapshot.class), any(AgentSnapshot.class));
    verify(matches, never()).save(any());
  }

  @Test
  void un_match_inexistente_falla_antes_de_llamar_al_motor() {
    MatchId missing = MatchId.newId();
    when(matches.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(missing)).isInstanceOf(MatchNotFoundException.class);
  }

  @Test
  void un_usuario_del_match_borrado_falla_antes_de_llamar_al_motor() {
    User userA = sampleUser();
    UserId userBId = UserId.newId();
    Match match = Match.propose(userA.id(), userBId, Duration.ofHours(12), CLOCK);

    when(matches.findById(match.id())).thenReturn(Optional.of(match));
    when(users.findById(userA.id())).thenReturn(Optional.of(userA));
    when(users.findById(userBId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(match.id())).isInstanceOf(UserNotFoundException.class);
  }

  private static User sampleUser() {
    Profile profile =
        new Profile(
            30,
            Gender.FEMALE,
            Set.of(Gender.MALE),
            SeekingType.LONG_TERM,
            "Buenos Aires",
            "arquitecta",
            List.of("cine"));
    SimulationParameters parameters =
        new SimulationParameters(
            AttachmentStyle.SECURE,
            0.4,
            new CommunicationProfile(0.2, 0.1, 0.2, 0.1),
            false,
            2,
            false,
            0.3,
            0.5);
    return User.register(profile, parameters, CLOCK);
  }
}
