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
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

  private final SecretKey key;
  private final Duration ttl;

  public JwtTokenIssuer(
      @Value("${pointeight.auth.jwt-secret}") String secret,
      @Value("${pointeight.auth.token-ttl-hours:24}") long ttlHours) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = Duration.ofHours(ttlHours);
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
