package com.pointeight.events;

import java.time.Instant;

/**
 * A structured, wire-safe view of one domain event, for M5's live "the System deciding" feed.
 * Deliberately not a formatted sentence: that's the admin panel's job, this only carries the
 * fields it needs to build one. {@code type} is the event's simple class name (e.g.
 * {@code "MatchAssignedEvent"}) — the panel switches on it the same way it already switches on
 * {@code MatchStatus} values coming from {@code MatchResponse}.
 */
public record RecentEvent(
    long sequence,
    String type,
    String matchId,
    UserSummary userA,
    UserSummary userB,
    Long expiryDurationSeconds,
    Instant occurredAt) {}
