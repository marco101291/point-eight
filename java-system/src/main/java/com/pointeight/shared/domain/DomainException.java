package com.pointeight.shared.domain;

/** Raíz de las excepciones de negocio. No extiende de nada de Spring a propósito. */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String message) {
    super(message);
  }
}
