package com.pointeight.shared.domain;

/** Se pidió un aggregate que no existe. El adapter HTTP la traduce a 404. */
public abstract class ResourceNotFoundException extends DomainException {

  protected ResourceNotFoundException(String message) {
    super(message);
  }
}
