package com.pointeight.config;

import com.pointeight.auth.infrastructure.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Merely adding {@code spring-boot-starter-security} to the classpath makes Spring Boot secure
 * every endpoint with HTTP Basic and a random generated password by default — everything below
 * replaces that with an explicit policy instead: only {@code /api/auth/me} actually requires a
 * valid token today (DEC-022's minimal, real proof that the JWT filter works end to end), every
 * other endpoint stays open exactly as before. The reveal endpoint (still unbuilt) is meant to
 * join {@code authenticated()} once it exists, not replace this wide-open default everywhere.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        // No HTTP session: identity is proven by the JWT on every request, not by a cookie.
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/api/auth/me").authenticated().anyRequest().permitAll())
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
