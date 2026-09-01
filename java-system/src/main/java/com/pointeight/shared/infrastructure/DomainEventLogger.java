package com.pointeight.shared.infrastructure;

import com.pointeight.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Leaves a trace of every domain event in the log. In M1 it's the only way to see the state
 * machine moving; in M4 the real listener that triggers the next match lives next to this one.
 */
@Component
public class DomainEventLogger {

  private static final Logger log = LoggerFactory.getLogger(DomainEventLogger.class);

  @EventListener
  public void on(DomainEvent event) {
    log.info("[event] {} :: {}", event.getClass().getSimpleName(), event);
  }
}
