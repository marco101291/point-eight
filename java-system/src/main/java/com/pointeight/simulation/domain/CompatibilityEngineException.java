package com.pointeight.simulation.domain;

/**
 * The Engine didn't respond or responded with an error. Pure Java on purpose: the REST adapter
 * translates the Spring exception here, so the port doesn't force anyone to know about
 * {@code RestClient}.
 */
public class CompatibilityEngineException extends RuntimeException {

  public CompatibilityEngineException(String message, Throwable cause) {
    super(message, cause);
  }
}
