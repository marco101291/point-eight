package com.pointeight.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.user.domain.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256";

  private final JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, 24);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final FilterChain chain = mock(FilterChain.class);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void a_valid_token_authenticates_as_the_user_it_names() throws Exception {
    UserId userId = UserId.newId();
    when(request.getHeader("Authorization")).thenReturn("Bearer " + issuer.issue(userId));

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo(userId.toString());
    verify(chain).doFilter(request, response);
  }

  @Test
  void no_authorization_header_leaves_the_request_unauthenticated() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void a_malformed_token_leaves_the_request_unauthenticated_instead_of_throwing() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer not-a-real-jwt");

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void a_token_signed_with_a_different_secret_leaves_the_request_unauthenticated() throws Exception {
    JwtTokenIssuer otherIssuer =
        new JwtTokenIssuer("a-completely-different-secret-of-32-bytes", 24);
    when(request.getHeader("Authorization"))
        .thenReturn("Bearer " + otherIssuer.issue(UserId.newId()));

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
