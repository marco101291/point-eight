package com.pointeight.shared.domain;

/** A nonexistent aggregate was requested. The HTTP adapter translates it to 404. */
public abstract class ResourceNotFoundException extends DomainException {

  protected ResourceNotFoundException(String message) {
    super(message);
  }
}
