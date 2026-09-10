package com.pointeight.auth.infrastructure;

import com.pointeight.auth.application.LoginUseCase;
import com.pointeight.auth.application.LogoutUseCase;
import com.pointeight.auth.application.RefreshAccessTokenUseCase;
import com.pointeight.auth.application.RegisterPushTokenUseCase;
import com.pointeight.auth.domain.Email;
import com.pointeight.user.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inbound HTTP adapter for login/refresh/logout. Translates to use cases, no logic of its own. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final LoginUseCase login;
  private final RefreshAccessTokenUseCase refresh;
  private final LogoutUseCase logout;
  private final RegisterPushTokenUseCase registerPushToken;

  public AuthController(
      LoginUseCase login,
      RefreshAccessTokenUseCase refresh,
      LogoutUseCase logout,
      RegisterPushTokenUseCase registerPushToken) {
    this.login = login;
    this.refresh = refresh;
    this.logout = logout;
    this.registerPushToken = registerPushToken;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return LoginResponse.from(login.execute(new Email(request.email()), request.password()));
  }

  /**
   * Trades a refresh token for a new access token (DEC-023) — no {@code Authorization} header
   * needed, and deliberately so: the whole point is to work *after* the access token has expired.
   * The refresh token itself, presented in the body, is what proves identity here.
   */
  @PostMapping("/refresh")
  public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return LoginResponse.from(refresh.execute(request.refreshToken()));
  }

  /**
   * Deletes the refresh token server-side — the access token itself can't be revoked before it
   * expires (it's a self-validating JWT), but this closes the window a lot faster than 24h used
   * to: the client can no longer silently get a new one once this token is gone.
   */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody LogoutRequest request) {
    logout.execute(request.refreshToken());
  }

  /**
   * Lets a client check whether the token it's holding is still valid, without needing a real
   * protected resource to call — {@code SecurityConfig} requires a valid JWT here specifically.
   */
  @GetMapping("/me")
  public MeResponse me(Authentication authentication) {
    return new MeResponse(authentication.getName());
  }

  /**
   * M7: the mobile client's Expo push token, tied to the caller's own account only — the token
   * identifies a device, the JWT identifies who's allowed to register one for it. Requires a valid
   * access token for the same reason {@code /me} does.
   */
  @PostMapping("/push-token")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void registerPushToken(
      @Valid @RequestBody RegisterPushTokenRequest request, Authentication authentication) {
    registerPushToken.execute(UserId.of(authentication.getName()), request.pushToken());
  }
}
