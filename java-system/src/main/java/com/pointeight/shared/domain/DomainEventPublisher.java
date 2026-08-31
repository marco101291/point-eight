package com.pointeight.shared.domain;

import java.util.Collection;

/**
 * Port de salida para publicar eventos de dominio. El dominio y los casos de uso dependen de esta
 * interfaz; el adapter que la implementa decide el mecanismo (Spring Events en M1, AMQP en M4).
 */
public interface DomainEventPublisher {

  void publish(DomainEvent event);

  default void publishAll(Collection<? extends DomainEvent> events) {
    events.forEach(this::publish);
  }
}
