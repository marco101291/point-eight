package com.pointeight.auth.application;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.InvalidCredentialsException;
import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.auth.domain.RefreshTokenRepository;
import com.pointeight.auth.domain.TokenIssuer;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues both halves of DEC-023's split: a short-lived access token ({@link TokenIssuer}, a JWT)
 * and a long-lived refresh token ({@link RefreshToken}, opaque and persisted) that {@link
 * RefreshAccessTokenUseCase} later exchanges for a fresh access token without asking for the
 * password again.
 */
@Service
public class LoginUseCase {

  private final AccountRepository accounts;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokenIssuer;
  private final RefreshTokenRepository refreshTokens;
  private final Clock clock;
  private final Duration refreshTokenTtl;

  public LoginUseCase(
      AccountRepository accounts,
      PasswordEncoder passwordEncoder,
      TokenIssuer tokenIssuer,
      RefreshTokenRepository refreshTokens,
      Clock clock,
      @Value("${pointeight.auth.refresh-token-ttl-days}") long refreshTokenTtlDays) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.tokenIssuer = tokenIssuer;
    this.refreshTokens = refreshTokens;
    this.clock = clock;
    this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
  }

  @Transactional
  public TokenPair execute(Email email, String rawPassword) {
    Account account = accounts.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(rawPassword, account.password().value())) {
      throw new InvalidCredentialsException();
    }
    String accessToken = tokenIssuer.issue(account.userId());
    RefreshToken.Issued issued =
        RefreshToken.issueNewFamily(account.userId(), refreshTokenTtl, clock);
    refreshTokens.save(issued.token());
    return new TokenPair(accessToken, issued.rawValue());
  }
}
