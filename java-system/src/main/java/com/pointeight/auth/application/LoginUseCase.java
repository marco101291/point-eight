package com.pointeight.auth.application;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.InvalidCredentialsException;
import com.pointeight.auth.domain.TokenIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

  private final AccountRepository accounts;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokenIssuer;

  public LoginUseCase(
      AccountRepository accounts, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.tokenIssuer = tokenIssuer;
  }

  public String execute(Email email, String rawPassword) {
    Account account = accounts.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(rawPassword, account.password().value())) {
      throw new InvalidCredentialsException();
    }
    return tokenIssuer.issue(account.userId());
  }
}
