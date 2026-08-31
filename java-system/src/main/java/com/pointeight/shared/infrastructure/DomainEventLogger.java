package com.pointeight.shared.infrastructure;

import com.pointeight.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deja rastro de cada evento de dominio en el log. En M1 es la única forma de ver la máquina de
 * estados moviéndose; en M4 el listener real que dispara el siguiente match vive al lado de este.
 */
@Component
public class DomainEventLogger {

  private static final Logger log = LoggerFactory.getLogger(DomainEventLogger.class);

  @EventListener
  public void on(DomainEvent event) {
    log.info("[evento] {} :: {}", event.getClass().getSimpleName(), event);
  }
}
