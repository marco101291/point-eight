package com.pointeight.push.domain;

/**
 * Expo's push service didn't accept the notification. Pure Java on purpose — same reasoning as
 * {@code CompatibilityEngineException}: the REST adapter translates the Spring exception here, so
 * the port doesn't force anyone to know about {@code RestClient}.
 */
public class PushNotificationException extends RuntimeException {

  public PushNotificationException(String message, Throwable cause) {
    super(message, cause);
  }
}
