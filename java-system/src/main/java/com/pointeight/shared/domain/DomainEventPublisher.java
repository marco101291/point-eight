package com.pointeight.shared.domain;

import java.util.Collection;

/**
 * Outbound port for publishing domain events. The domain and use cases depend on this interface;
 * the adapter that implements it decides the mechanism (Spring Events in M1, AMQP in M4).
 */
public interface DomainEventPublisher {

  void publish(DomainEvent event);

  default void publishAll(Collection<? extends DomainEvent> events) {
    events.forEach(this::publish);
  }
}
