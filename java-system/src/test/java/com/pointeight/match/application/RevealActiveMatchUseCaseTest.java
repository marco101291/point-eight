package com.pointeight.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
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

class RevealActiveMatchUseCaseTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final Duration TWELVE_HOURS = Duration.ofHours(12);

  private MatchRepository matches;
  private UserRepository users;
  private RevealActiveMatchUseCase useCase;

  @BeforeEach
  void setUp() {
    matches = mock(MatchRepository.class);
    users = mock(UserRepository.class);
    useCase = new RevealActiveMatchUseCase(matches, users);
  }

  @Test
  void devuelve_el_perfil_de_la_contraparte_y_cuando_expira_el_match_ACTIVE() {
    User requester = sampleUser("Rosario", "docente");
    User counterpart = sampleUser("Buenos Aires", "arquitecta");
    Match match = Match.propose(requester.id(), counterpart.id(), TWELVE_HOURS, CLOCK);
    match.activate(CLOCK);

    when(matches.findByUser(requester.id())).thenReturn(List.of(match));
    when(users.findById(counterpart.id())).thenReturn(Optional.of(counterpart));

    ActiveMatchReveal revealed = useCase.execute(requester.id());

    assertThat(revealed.profile()).isEqualTo(counterpart.profile());
    assertThat(revealed.expiresAt())
        .isEqualTo(Instant.parse("2026-01-01T00:00:00Z").plus(TWELVE_HOURS));
  }

  @Test
  void funciona_sin_importar_de_que_lado_del_match_este_el_que_pide() {
    User requester = sampleUser("Rosario", "docente");
    User counterpart = sampleUser("Buenos Aires", "arquitecta");
    // requester is userB this time, not userA
    Match match = Match.propose(counterpart.id(), requester.id(), TWELVE_HOURS, CLOCK);
    match.activate(CLOCK);

    when(matches.findByUser(requester.id())).thenReturn(List.of(match));
    when(users.findById(counterpart.id())).thenReturn(Optional.of(counterpart));

    assertThat(useCase.execute(requester.id()).profile()).isEqualTo(counterpart.profile());
  }

  @Test
  void sin_match_ACTIVE_no_hay_nada_que_revelar() {
    User requester = sampleUser("Rosario", "docente");
    User counterpart = sampleUser("Buenos Aires", "arquitecta");
    Match pending = Match.propose(requester.id(), counterpart.id(), TWELVE_HOURS, CLOCK);

    when(matches.findByUser(requester.id())).thenReturn(List.of(pending));

    assertThatThrownBy(() -> useCase.execute(requester.id()))
        .isInstanceOf(NoActiveMatchException.class);
  }

  @Test
  void sin_ningun_match_no_hay_nada_que_revelar() {
    UserId requesterId = UserId.newId();
    when(matches.findByUser(requesterId)).thenReturn(List.of());

    assertThatThrownBy(() -> useCase.execute(requesterId))
        .isInstanceOf(NoActiveMatchException.class);
  }

  @Test
  void una_contraparte_borrada_entre_medio_falla_igual() {
    User requester = sampleUser("Rosario", "docente");
    UserId counterpartId = UserId.newId();
    Match match = Match.propose(requester.id(), counterpartId, TWELVE_HOURS, CLOCK);
    match.activate(CLOCK);

    when(matches.findByUser(requester.id())).thenReturn(List.of(match));
    when(users.findById(counterpartId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(requester.id()))
        .isInstanceOf(UserNotFoundException.class);
  }

  private static User sampleUser(String city, String profession) {
    Profile profile =
        new Profile(
            30,
            Gender.FEMALE,
            Set.of(Gender.MALE),
            SeekingType.LONG_TERM,
            city,
            profession,
            List.of("cine"),
            "https://picsum.photos/seed/test/900/1400");
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
