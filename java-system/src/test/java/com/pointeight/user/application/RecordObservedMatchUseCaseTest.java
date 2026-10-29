package com.pointeight.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.user.domain.ConfidenceScore;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecordObservedMatchUseCaseTest {

  private static final Instant T0 = Instant.parse("2026-08-29T12:00:00Z");

  private final UserRepository users = mock(UserRepository.class);
  private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
  private final RecordObservedMatchUseCase useCase = new RecordObservedMatchUseCase(users, clock);

  private User registeredUser() {
    Profile profile =
        new Profile(
            30,
            Gender.FEMALE,
            Set.of(Gender.MALE),
            SeekingType.LONG_TERM,
            "Guadalajara",
            "docente",
            List.of("cine"),
            "https://picsum.photos/seed/test/900/1400");
    return User.register(profile, null, clock);
  }

  @Test
  void refuerza_la_confianza_del_usuario_y_lo_guarda() {
    User user = registeredUser();
    when(users.findById(user.id())).thenReturn(Optional.of(user));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User saved = useCase.execute(user.id());

    assertThat(saved.cumulativeConfidenceScore())
        .isEqualTo(ConfidenceScore.initial().reinforced());
    verify(users).save(user);
  }

  @Test
  void un_usuario_inexistente_lanza_UserNotFoundException_sin_guardar_nada() {
    UserId missing = UserId.newId();
    when(users.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(missing))
        .isInstanceOf(UserNotFoundException.class);
    verify(users, never()).save(any());
  }
}
