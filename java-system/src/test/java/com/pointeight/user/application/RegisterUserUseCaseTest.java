package com.pointeight.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.shared.domain.DomainEventPublisher;
import com.pointeight.user.domain.ConfidenceScore;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.TraitDerivation;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserRepository;
import com.pointeight.user.domain.event.UserRegisteredEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegisterUserUseCaseTest {

  private static final Instant T0 = Instant.parse("2026-08-29T12:00:00Z");

  private final UserRepository users = mock(UserRepository.class);
  private final DomainEventPublisher events = mock(DomainEventPublisher.class);
  private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
  private final RegisterUserUseCase useCase = new RegisterUserUseCase(users, events, clock);

  private Profile sampleProfile() {
    return new Profile(
        30,
        Gender.FEMALE,
        Set.of(Gender.MALE),
        SeekingType.LONG_TERM,
        "Guadalajara",
        "docente",
        List.of("cine"),
        "https://picsum.photos/seed/test/900/1400");
  }

  @Test
  void guarda_al_usuario_y_publica_UserRegisteredEvent() {
    Profile profile = sampleProfile();
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User registered = useCase.execute(profile, null);

    verify(events).publishAll(List.of(new UserRegisteredEvent(registered.id(), T0)));
  }

  @Test
  void devuelve_lo_que_el_repositorio_guarda_no_el_usuario_recien_creado() {
    Profile profile = sampleProfile();
    User fromRepository =
        User.rehydrate(
            UserId.newId(), profile, TraitDerivation.defaultsFor(profile),
            ConfidenceScore.initial(), T0, T0);
    when(users.save(any(User.class))).thenReturn(fromRepository);

    User result = useCase.execute(profile, null);

    assertThat(result).isEqualTo(fromRepository);
  }
}
