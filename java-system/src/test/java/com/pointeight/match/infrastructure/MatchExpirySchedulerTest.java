package com.pointeight.match.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.match.application.MatchLifecycleUseCases;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchExpirySchedulerTest {

  private static final Instant T0 = Instant.parse("2026-08-29T12:00:00Z");

  private final MatchRepository matches = mock(MatchRepository.class);
  private final MatchLifecycleUseCases lifecycle = mock(MatchLifecycleUseCases.class);

  private Match activatedAt(Instant activationInstant, Duration expiryDuration) {
    Clock activationClock = Clock.fixed(activationInstant, ZoneOffset.UTC);
    Match match =
        Match.propose(UserId.newId(), UserId.newId(), expiryDuration, activationClock);
    match.activate(activationClock);
    match.pullEvents();
    return match;
  }

  @Test
  void expira_los_matches_ACTIVE_que_ya_pasaron_su_expiresAt() {
    Match due = activatedAt(T0, Duration.ofHours(1));
    Match notDue = activatedAt(T0, Duration.ofHours(100));
    Clock schedulerClock = Clock.fixed(T0.plus(Duration.ofHours(2)), ZoneOffset.UTC);
    MatchExpiryScheduler scheduler =
        new MatchExpiryScheduler(matches, lifecycle, schedulerClock);
    when(matches.count(MatchStatus.ACTIVE)).thenReturn(2L);
    when(matches.findAll(MatchStatus.ACTIVE, 0, 2)).thenReturn(List.of(due, notDue));

    scheduler.expireDueMatches();

    verify(lifecycle).expire(due.id());
    verify(lifecycle, never()).expire(notDue.id());
  }

  @Test
  void sin_matches_ACTIVE_no_consulta_nada_mas() {
    Clock schedulerClock = Clock.fixed(T0, ZoneOffset.UTC);
    MatchExpiryScheduler scheduler =
        new MatchExpiryScheduler(matches, lifecycle, schedulerClock);
    when(matches.count(MatchStatus.ACTIVE)).thenReturn(0L);

    scheduler.expireDueMatches();

    verify(matches, never()).findAll(any(MatchStatus.class), anyInt(), anyInt());
  }

  @Test
  void una_falla_al_expirar_uno_no_detiene_a_los_demas() {
    Match dueA = activatedAt(T0, Duration.ofHours(1));
    Match dueB = activatedAt(T0, Duration.ofHours(1));
    Clock schedulerClock = Clock.fixed(T0.plus(Duration.ofHours(2)), ZoneOffset.UTC);
    MatchExpiryScheduler scheduler =
        new MatchExpiryScheduler(matches, lifecycle, schedulerClock);
    when(matches.count(MatchStatus.ACTIVE)).thenReturn(2L);
    when(matches.findAll(MatchStatus.ACTIVE, 0, 2)).thenReturn(List.of(dueA, dueB));
    when(lifecycle.expire(dueA.id())).thenThrow(new RuntimeException("boom"));

    scheduler.expireDueMatches();

    verify(lifecycle).expire(dueB.id());
  }
}
