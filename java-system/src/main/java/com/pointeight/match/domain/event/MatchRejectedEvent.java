package com.pointeight.match.domain.event;

import com.pointeight.match.domain.MatchId;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import java.time.Instant;

/**
 * The match was cut short before running its course. Unlike {@link MatchExpiredEvent}, it can
 * happen from {@code PENDING} too, so it carries whether the match ever went {@code ACTIVE}: a
 * rejection that never started observed nothing about either user (DEC-026's recalibration only
 * counts matches that actually ran).
 */
public record MatchRejectedEvent(
    MatchId matchId, UserId userAId, UserId userBId, boolean wasActive, Instant occurredAt)
    implements DomainEvent {}
