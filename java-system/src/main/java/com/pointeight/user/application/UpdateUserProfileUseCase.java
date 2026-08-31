package com.pointeight.user.application;

import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edición de Capa 1. La Capa 2 no se toca por esta vía: el usuario no la controla. */
@Service
public class UpdateUserProfileUseCase {

  private final UserRepository users;
  private final Clock clock;

  public UpdateUserProfileUseCase(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public User execute(UserId id, Profile profile) {
    User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    user.updateProfile(profile, clock);
    return users.save(user);
  }
}
