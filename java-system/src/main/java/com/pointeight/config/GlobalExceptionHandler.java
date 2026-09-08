package com.pointeight.config;

import com.pointeight.auth.domain.EmailAlreadyRegisteredException;
import com.pointeight.auth.domain.InvalidCredentialsException;
import com.pointeight.match.application.UserAlreadyMatchedException;
import com.pointeight.match.domain.IllegalMatchTransitionException;
import com.pointeight.shared.domain.ResourceNotFoundException;
import com.pointeight.simulation.domain.CompatibilityEngineException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions to HTTP responses. Without this, an illegal transition — a
 * perfectly expected state conflict — would come out as a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final URI NOT_FOUND = URI.create("urn:pointeight:not-found");
  private static final URI ILLEGAL_TRANSITION = URI.create("urn:pointeight:illegal-transition");
  private static final URI ALREADY_MATCHED = URI.create("urn:pointeight:already-matched");
  private static final URI EMAIL_TAKEN = URI.create("urn:pointeight:email-already-registered");
  private static final URI INVALID_CREDENTIALS = URI.create("urn:pointeight:invalid-credentials");
  private static final URI INVALID_REQUEST = URI.create("urn:pointeight:invalid-request");
  private static final URI ENGINE_UNAVAILABLE = URI.create("urn:pointeight:engine-unavailable");

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail onNotFound(ResourceNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage(), NOT_FOUND);
  }

  @ExceptionHandler(IllegalMatchTransitionException.class)
  public ProblemDetail onIllegalTransition(IllegalMatchTransitionException e) {
    ProblemDetail detail =
        problem(HttpStatus.CONFLICT, "Transition not allowed", e.getMessage(), ILLEGAL_TRANSITION);
    detail.setProperty("from", e.from());
    detail.setProperty("to", e.to());
    detail.setProperty("allowedTransitions", e.from().allowedTransitions());
    return detail;
  }

  @ExceptionHandler(UserAlreadyMatchedException.class)
  public ProblemDetail onAlreadyMatched(UserAlreadyMatchedException e) {
    return problem(HttpStatus.CONFLICT, "User already matched", e.getMessage(), ALREADY_MATCHED);
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ProblemDetail onEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
    return problem(HttpStatus.CONFLICT, "Email already registered", e.getMessage(), EMAIL_TAKEN);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail onInvalidCredentials(InvalidCredentialsException e) {
    return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", e.getMessage(), INVALID_CREDENTIALS);
  }

  @ExceptionHandler(CompatibilityEngineException.class)
  public ProblemDetail onEngineUnavailable(CompatibilityEngineException e) {
    return problem(
        HttpStatus.BAD_GATEWAY, "Engine unavailable", e.getMessage(), ENGINE_UNAVAILABLE);
  }

  /** Value Object invariants: arrive as IllegalArgumentException from the domain. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail onIllegalArgument(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage(), INVALID_REQUEST);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail onIllegalState(IllegalStateException e) {
    return problem(HttpStatus.CONFLICT, "Invalid state", e.getMessage(), ILLEGAL_TRANSITION);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail onValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
            .reduce((a, b) -> a + "; " + b)
            .orElse("Invalid request");
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", message, INVALID_REQUEST);
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail, URI type) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(type);
    return problem;
  }
}
