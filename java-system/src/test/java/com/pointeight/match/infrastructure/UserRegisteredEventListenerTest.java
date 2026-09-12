package com.pointeight.match.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.match.application.AssignNextMatchUseCase;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.event.UserRegisteredEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserRegisteredEventListenerTest {

  private final AssignNextMatchUseCase assignNextMatch = mock(AssignNextMatchUseCase.class);
  private final UserRegisteredEventListener listener =
      new UserRegisteredEventListener(assignNextMatch);

  @Test
  void trata_de_asignarle_un_primer_match_al_usuario_recien_registrado() {
    UserId userId = UserId.newId();
    UserRegisteredEvent event = new UserRegisteredEvent(userId, Instant.now());

    listener.on(event);

    verify(assignNextMatch).execute(userId);
  }

  @Test
  void una_falla_al_asignar_no_se_propaga() {
    UserId userId = UserId.newId();
    UserRegisteredEvent event = new UserRegisteredEvent(userId, Instant.now());
    when(assignNextMatch.execute(userId)).thenThrow(new RuntimeException("boom"));

    listener.on(event);

    verify(assignNextMatch).execute(userId);
  }
}
