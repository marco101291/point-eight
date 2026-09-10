package com.pointeight.config;

import com.pointeight.auth.infrastructure.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Merely adding {@code spring-boot-starter-security} to the classpath makes Spring Boot secure
 * every endpoint with HTTP Basic and a random generated password by default — everything below
 * replaces that with an explicit policy instead. {@code /api/auth/me} (DEC-022's minimal, real
 * proof that the JWT filter works end to end) and {@code /api/matches/me/reveal} (DEC-021's
 * reveal endpoint — the caller's identity has to come from the token, not a path parameter, or
 * anyone could reveal anyone else's match) are the only two that require a valid token; every
 * other endpoint stays open exactly as before.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final List<String> corsAllowedOriginPatterns;

  public SecurityConfig(
      @Value("${pointeight.cors.allowed-origin-patterns}") List<String> corsAllowedOriginPatterns) {
    this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Only {@code mobile-client}'s web target ({@code expo start --web}) ever needs this: native
   * iOS/Android has no same-origin policy to enforce, and the admin panel fetches java-system
   * server-side (its Next.js server component, not the browser — see the main repo's CLAUDE.md),
   * so it was never subject to CORS either. Without this bean, Spring Security rejects the
   * preflight {@code OPTIONS} outright ("Invalid CORS request") before the real request is ever
   * sent — the login screen sees that as a generic network failure, not a 4xx it can report.
   * Patterns, not a fixed origin list, because Expo picks its dev port dynamically (8081, 8082…).
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(corsAllowedOriginPatterns);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // No HTTP session: identity is proven by the JWT on every request, not by a cookie.
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/me", "/api/auth/push-token", "/api/matches/me/reveal")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        // Spring Security's own default for "no credentials at all" is 403, not 401 — wrong here:
        // 401 means "say who you are", 403 means "I know who you are and it's not enough".
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
