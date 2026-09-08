package com.pointeight.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pointeight.user.domain.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenIssuerTest {

  private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256";

  private final JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, 24);

  @Test
  void the_issued_token_carries_the_user_id_as_its_subject() {
    UserId userId = UserId.newId();

    String token = issuer.issue(userId);
    Claims claims = parse(token);

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
  }

  @Test
  void the_issued_token_expires_roughly_the_configured_hours_from_now() {
    UserId userId = UserId.newId();

    Instant before = Instant.now();
    String token = issuer.issue(userId);
    Claims claims = parse(token);

    Duration untilExpiry = Duration.between(before, claims.getExpiration().toInstant());
    assertThat(untilExpiry).isCloseTo(Duration.ofHours(24), Duration.ofSeconds(5));
  }

  @Test
  void a_token_signed_with_a_different_secret_does_not_verify() {
    JwtTokenIssuer otherIssuer = new JwtTokenIssuer("a-completely-different-secret-of-32-bytes", 24);
    String token = otherIssuer.issue(UserId.newId());

    assertThatThrownBy(() -> parse(token)).isInstanceOf(SignatureException.class);
  }

  private static Claims parse(String token) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
