package com.pointeight.shared.domain;

/** Root of business exceptions. Deliberately doesn't extend anything from Spring. */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String message) {
    super(message);
  }
}
