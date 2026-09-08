package com.pointeight.auth.infrastructure;

import com.pointeight.auth.application.LoginUseCase;
import com.pointeight.auth.domain.Email;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inbound HTTP adapter for login. Translates to the use case, no logic of its own. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final LoginUseCase login;

  public AuthController(LoginUseCase login) {
    this.login = login;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    String token = login.execute(new Email(request.email()), request.password());
    return new LoginResponse(token);
  }

  /**
   * Lets a client check whether the token it's holding is still valid, without needing a real
   * protected resource to call — {@code SecurityConfig} requires a valid JWT here specifically.
   */
  @GetMapping("/me")
  public MeResponse me(Authentication authentication) {
    return new MeResponse(authentication.getName());
  }
}
