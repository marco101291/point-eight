package com.pointeight.auth.domain;

import com.pointeight.shared.domain.DomainException;

/** Deliberately doesn't distinguish "no such email" from "wrong password" in its message. */
public class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}
