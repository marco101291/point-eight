package com.pointeight.user.application;

import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads of the User aggregate. */
@Service
@Transactional(readOnly = true)
public class UserQueries {

  private final UserRepository users;

  public UserQueries(UserRepository users) {
    this.users = users;
  }

  public User byId(UserId id) {
    return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
  }

  public List<User> page(int page, int size) {
    return users.findAll(page, size);
  }

  public long total() {
    return users.count();
  }
}
