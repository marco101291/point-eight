package com.pointeight.match.domain.event;

import com.pointeight.match.domain.MatchId;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import java.time.Instant;

/**
 * The match ran its course. From M4 on, this event is what triggers the search for the next
 * candidate for both users.
 */
public record MatchExpiredEvent(
    MatchId matchId, UserId userAId, UserId userBId, Instant occurredAt) implements DomainEvent {}
