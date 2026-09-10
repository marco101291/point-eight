package com.pointeight.auth.domain;

import com.pointeight.shared.domain.DomainException;

/**
 * Doesn't distinguish "never existed" from "expired" from "already rotated" — same reasoning
 * {@link InvalidCredentialsException} applies to a wrong password: any of those means the caller
 * needs to log in again, and none of them is this system's business to explain.
 */
public class InvalidRefreshTokenException extends DomainException {

  public InvalidRefreshTokenException() {
    super("Invalid or expired refresh token");
  }
}
