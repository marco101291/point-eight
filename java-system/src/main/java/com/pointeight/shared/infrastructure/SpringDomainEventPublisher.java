package com.pointeight.shared.infrastructure;

import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.shared.domain.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Adapter of the event port on Spring's in-process bus. In M4 it coexists with the AMQP one. */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher delegate;

  public SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
    this.delegate = delegate;
  }

  @Override
  public void publish(DomainEvent event) {
    delegate.publishEvent(event);
  }
}
