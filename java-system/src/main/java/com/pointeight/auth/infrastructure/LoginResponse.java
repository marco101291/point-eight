package com.pointeight.auth.infrastructure;

import com.pointeight.auth.application.TokenPair;

/** Also what {@code POST /api/auth/refresh} returns — a rotated pair, same shape as login's. */
public record LoginResponse(String accessToken, String refreshToken) {

  public static LoginResponse from(TokenPair pair) {
    return new LoginResponse(pair.accessToken(), pair.refreshToken());
  }
}
