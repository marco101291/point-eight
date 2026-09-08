package com.pointeight.auth.application;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.EmailAlreadyRegisteredException;
import com.pointeight.auth.domain.HashedPassword;
import com.pointeight.user.application.RegisterUserUseCase;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SimulationParameters;
import com.pointeight.user.domain.User;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration is one user action creating two things — a dating profile ({@link User}) and login
 * credentials ({@link Account}) — orchestrated here, in neither feature's own application layer,
 * so {@code user} doesn't need to know {@code auth} exists (DEC-022).
 */
@Service
public class RegisterAccountUseCase {

  private final RegisterUserUseCase registerUser;
  private final AccountRepository accounts;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public RegisterAccountUseCase(
      RegisterUserUseCase registerUser,
      AccountRepository accounts,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.registerUser = registerUser;
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public User execute(
      Email email, String rawPassword, Profile profile, SimulationParameters parameters) {
    if (accounts.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException(email);
    }
    User user = registerUser.execute(profile, parameters);
    HashedPassword hashed = new HashedPassword(passwordEncoder.encode(rawPassword));
    accounts.save(Account.register(user.id(), email, hashed, clock));
    return user;
  }
}
