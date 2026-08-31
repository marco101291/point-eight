package com.pointeight.shared.domain;

import java.time.Instant;

/** Marcador de todo evento de dominio. Los eventos son inmutables y describen algo que ya pasó. */
public interface DomainEvent {

  /** Momento en que el evento ocurrió, no en que se publicó. */
  Instant occurredAt();
}
