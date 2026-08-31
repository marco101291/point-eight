package com.pointeight.config;

import com.pointeight.match.application.UserAlreadyMatchedException;
import com.pointeight.match.domain.IllegalMatchTransitionException;
import com.pointeight.shared.domain.ResourceNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce excepciones de dominio a respuestas HTTP. Sin esto, una transición ilegal — que es un
 * conflicto de estado perfectamente esperable — saldría como un 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final URI NOT_FOUND = URI.create("urn:pointeight:not-found");
  private static final URI ILLEGAL_TRANSITION = URI.create("urn:pointeight:illegal-transition");
  private static final URI ALREADY_MATCHED = URI.create("urn:pointeight:already-matched");
  private static final URI INVALID_REQUEST = URI.create("urn:pointeight:invalid-request");

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail onNotFound(ResourceNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage(), NOT_FOUND);
  }

  @ExceptionHandler(IllegalMatchTransitionException.class)
  public ProblemDetail onIllegalTransition(IllegalMatchTransitionException e) {
    ProblemDetail detail =
        problem(HttpStatus.CONFLICT, "Transición no permitida", e.getMessage(), ILLEGAL_TRANSITION);
    detail.setProperty("from", e.from());
    detail.setProperty("to", e.to());
    detail.setProperty("allowedTransitions", e.from().allowedTransitions());
    return detail;
  }

  @ExceptionHandler(UserAlreadyMatchedException.class)
  public ProblemDetail onAlreadyMatched(UserAlreadyMatchedException e) {
    return problem(HttpStatus.CONFLICT, "Usuario ocupado", e.getMessage(), ALREADY_MATCHED);
  }

  /** Invariantes de los Value Objects: llegan como IllegalArgumentException desde el dominio. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail onIllegalArgument(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, "Petición inválida", e.getMessage(), INVALID_REQUEST);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail onIllegalState(IllegalStateException e) {
    return problem(HttpStatus.CONFLICT, "Estado inválido", e.getMessage(), ILLEGAL_TRANSITION);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail onValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
            .reduce((a, b) -> a + "; " + b)
            .orElse("Petición inválida");
    return problem(HttpStatus.BAD_REQUEST, "Petición inválida", message, INVALID_REQUEST);
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail, URI type) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(type);
    return problem;
  }
}
