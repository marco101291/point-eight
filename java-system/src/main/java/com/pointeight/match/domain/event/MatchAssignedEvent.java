package com.pointeight.match.domain.event;

import com.pointeight.match.domain.MatchId;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import java.time.Duration;
import java.time.Instant;

/** The System assigned a match. Unilateral: nobody chose it or accepted it. */
public record MatchAssignedEvent(
    MatchId matchId, UserId userAId, UserId userBId, Duration expiryDuration, Instant occurredAt)
    implements DomainEvent {}
