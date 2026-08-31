package com.pointeight.user.application;

import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteUserUseCase {

  private final UserRepository users;

  public DeleteUserUseCase(UserRepository users) {
    this.users = users;
  }

  @Transactional
  public void execute(UserId id) {
    if (!users.existsById(id)) {
      throw new UserNotFoundException(id);
    }
    users.deleteById(id);
  }
}
