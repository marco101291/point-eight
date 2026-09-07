package com.pointeight.events;

/**
 * Just enough Layer 1 to make a feed line readable — the domain has no name field at all (see
 * {@code Profile}), so an id alone is what the feed would otherwise be stuck showing. Snapshotted
 * at the moment the event fired, not looked up fresh when the panel polls: a user's city or
 * profession changing later shouldn't rewrite what an already-recorded event says.
 */
public record UserSummary(String id, String city, String profession) {}
