package com.pointeight.match.domain.event;

import com.pointeight.match.domain.MatchId;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import java.time.Instant;

/**
 * El match llegó a término. A partir de M4 este evento es el que dispara la búsqueda del siguiente
 * candidato para ambos usuarios.
 */
public record MatchExpiredEvent(
    MatchId matchId, UserId userAId, UserId userBId, Instant occurredAt) implements DomainEvent {}
