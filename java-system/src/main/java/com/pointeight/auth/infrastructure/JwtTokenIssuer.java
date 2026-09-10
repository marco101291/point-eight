package com.pointeight.auth.infrastructure;

import com.pointeight.auth.domain.TokenIssuer;
import com.pointeight.user.domain.UserId;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Self-issued, self-validated JWTs (DEC-022) — a shared HMAC secret, not Spring Security's OAuth2
 * Resource Server module, which is built around trusting an external JWK endpoint this system
 * doesn't have.
 *
 * <p>Short-lived on purpose since DEC-023: nobody can revoke one of these before it expires (it's
 * self-validating, no database lookup involved), so keeping the window small is the only lever
 * there is. {@link com.pointeight.auth.domain.RefreshToken} is what makes that tolerable — a
 * client renews its access token long before the user notices, without logging in again.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

  private final SecretKey key;
  private final Duration ttl;

  public JwtTokenIssuer(
      @Value("${pointeight.auth.jwt-secret}") String secret,
      @Value("${pointeight.auth.access-token-ttl-minutes:15}") long ttlMinutes) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = Duration.ofMinutes(ttlMinutes);
  }

  @Override
  public String issue(UserId userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }
}
