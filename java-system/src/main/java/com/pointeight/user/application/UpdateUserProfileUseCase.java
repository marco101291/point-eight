package com.pointeight.user.application;

import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Layer 1 editing. Layer 2 isn't touched through this path: the user doesn't control it. */
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
