package com.pointeight.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.EmailAlreadyRegisteredException;
import com.pointeight.user.application.RegisterUserUseCase;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterAccountUseCaseTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  private final RegisterUserUseCase registerUser = mock(RegisterUserUseCase.class);
  private final AccountRepository accounts = mock(AccountRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final RegisterAccountUseCase useCase =
      new RegisterAccountUseCase(registerUser, accounts, passwordEncoder, CLOCK);

  private static Profile sampleProfile() {
    return new Profile(
        28, Gender.MALE, Set.of(Gender.FEMALE), SeekingType.LONG_TERM, "Madrid", "docente",
        List.of());
  }

  @Test
  void creates_the_user_then_the_linked_account() {
    Email email = new Email("marco@example.com");
    Profile profile = sampleProfile();
    User user = User.register(profile, null, CLOCK);
    when(accounts.existsByEmail(email)).thenReturn(false);
    when(registerUser.execute(profile, null)).thenReturn(user);
    when(passwordEncoder.encode("hunter2hunter2")).thenReturn("bcrypt-hash");

    User result = useCase.execute(email, "hunter2hunter2", profile, null);

    assertThat(result).isEqualTo(user);
    verify(accounts).save(argThatAccountFor(user, email, "bcrypt-hash"));
  }

  @Test
  void rejects_an_email_already_in_use_without_creating_a_user() {
    Email email = new Email("marco@example.com");
    Profile profile = sampleProfile();
    when(accounts.existsByEmail(email)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(email, "hunter2hunter2", profile, null))
        .isInstanceOf(EmailAlreadyRegisteredException.class);

    verify(registerUser, never()).execute(any(), any());
    verify(accounts, never()).save(any());
  }

  private static Account argThatAccountFor(User user, Email email, String passwordHash) {
    return argThat(
        account ->
            account.userId().equals(user.id())
                && account.email().equals(email)
                && account.password().value().equals(passwordHash));
  }
}
