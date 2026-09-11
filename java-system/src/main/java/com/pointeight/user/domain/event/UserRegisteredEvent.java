package com.pointeight.user.domain.event;

import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.UserId;
import java.time.Instant;

/**
 * A new user registered. The trigger point for assigning them a first match: before this,
 * nothing observed registration at all, so a brand-new user only ever got matched if some other
 * match's expiry happened to search for a candidate and happened to pick them.
 */
public record UserRegisteredEvent(UserId userId, Instant occurredAt) implements DomainEvent {}
