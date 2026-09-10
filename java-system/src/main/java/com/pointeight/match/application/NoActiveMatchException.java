package com.pointeight.match.application;

import com.pointeight.shared.domain.ResourceNotFoundException;
import com.pointeight.user.domain.UserId;

/** The reveal endpoint has nothing to show: the caller has no ACTIVE match right now. */
public class NoActiveMatchException extends ResourceNotFoundException {

  public NoActiveMatchException(UserId userId) {
    super("User %s has no active match".formatted(userId));
  }
}
