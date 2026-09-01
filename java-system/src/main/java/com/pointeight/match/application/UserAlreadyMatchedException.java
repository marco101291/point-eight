package com.pointeight.match.application;

import com.pointeight.shared.domain.DomainException;
import com.pointeight.user.domain.UserId;

/**
 * In the compound, each person is in exactly one match at a time. Translated to HTTP 409.
 */
public class UserAlreadyMatchedException extends DomainException {

  public UserAlreadyMatchedException(UserId userId) {
    super("User %s already has an unfinished match".formatted(userId));
  }
}
