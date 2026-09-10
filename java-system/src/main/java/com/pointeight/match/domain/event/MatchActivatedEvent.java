package com.pointeight.match.domain.event;

import com.pointeight.match.domain.MatchId;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import java.time.Instant;

/**
 * A match went live. The trigger point for push notifications (M7): unlike
 * {@code MatchAssignedEvent}/{@code MatchExpiredEvent}, nothing observed this transition before —
 * {@code activate()} used to just flip the status.
 */
public record MatchActivatedEvent(MatchId matchId, UserId userAId, UserId userBId, Instant occurredAt)
    implements DomainEvent {}
