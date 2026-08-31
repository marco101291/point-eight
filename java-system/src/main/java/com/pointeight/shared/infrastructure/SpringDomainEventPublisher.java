package com.pointeight.shared.infrastructure;

import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.shared.domain.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Adapter del port de eventos sobre el bus in-process de Spring. En M4 convive con el de AMQP. */
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
