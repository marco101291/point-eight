package com.pointeight.user.application;

import com.pointeight.shared.domain.DomainEventPublisher;
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
  private final DomainEventPublisher events;
  private final Clock clock;

  public RegisterUserUseCase(UserRepository users, DomainEventPublisher events, Clock clock) {
    this.users = users;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public User execute(Profile profile, SimulationParameters parameters) {
    User user = User.register(profile, parameters, clock);
    User saved = users.save(user);
    events.publishAll(user.pullEvents());
    return saved;
  }
}
