package com.pointeight.auth.domain;

import com.pointeight.shared.domain.DomainException;

public class EmailAlreadyRegisteredException extends DomainException {

  public EmailAlreadyRegisteredException(Email email) {
    super("Email already registered: " + email);
  }
}
