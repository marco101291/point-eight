package com.pointeight.user.application;

import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SimulationParameters;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User registration. If Layer 2 isn't provided, the System derives it from the profile. */
@Service
public class RegisterUserUseCase {

  private final UserRepository users;
  private final Clock clock;

  public RegisterUserUseCase(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public User execute(Profile profile, SimulationParameters parameters) {
    return users.save(User.register(profile, parameters, clock));
  }
}
