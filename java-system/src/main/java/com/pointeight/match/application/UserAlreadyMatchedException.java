package com.pointeight.match.application;

import com.pointeight.shared.domain.DomainException;
import com.pointeight.user.domain.UserId;

/**
 * En el compound cada persona está en un único match a la vez. Se traduce a HTTP 409.
 */
public class UserAlreadyMatchedException extends DomainException {

  public UserAlreadyMatchedException(UserId userId) {
    super("El usuario %s ya tiene un match sin terminar".formatted(userId));
  }
}
