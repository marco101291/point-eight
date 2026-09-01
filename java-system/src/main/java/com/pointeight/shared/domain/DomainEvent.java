package com.pointeight.shared.domain;

import java.time.Instant;

/**
 * Marker for every domain event. Events are immutable and describe something that already
 * happened.
 */
public interface DomainEvent {

  /** When the event occurred, not when it was published. */
  Instant occurredAt();
}
