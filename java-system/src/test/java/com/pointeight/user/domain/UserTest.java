package com.pointeight.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pointeight.user.domain.event.UserRegisteredEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final Instant T0 = Instant.parse("2026-08-29T12:00:00Z");
  private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

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
  void registrarse_emite_UserRegisteredEvent() {
    User user = User.register(sampleProfile(), null, clock);

    assertThat(user.pullEvents())
        .containsExactly(new UserRegisteredEvent(user.id(), T0));
  }

  @Test
  void pullEvents_vacia_el_buffer() {
    User user = User.register(sampleProfile(), null, clock);
    user.pullEvents();

    assertThat(user.pullEvents()).isEmpty();
  }

  @Test
  void rehidratar_no_emite_eventos() {
    User user =
        User.rehydrate(
            UserId.newId(),
            sampleProfile(),
            TraitDerivation.defaultsFor(sampleProfile()),
            ConfidenceScore.initial(),
            T0,
            T0);

    assertThat(user.pullEvents()).isEmpty();
  }
}
