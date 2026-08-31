package com.pointeight.user.domain;

import com.pointeight.shared.domain.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

  public UserNotFoundException(UserId id) {
    super("No existe el usuario " + id);
  }
}
