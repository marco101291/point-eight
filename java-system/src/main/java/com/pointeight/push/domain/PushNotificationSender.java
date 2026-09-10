package com.pointeight.push.domain;

/**
 * Sends a push notification to a single device. A port: the domain and application layers don't
 * know or care that the implementation happens to be Expo's push service specifically — the same
 * reasoning {@link com.pointeight.auth.domain.TokenIssuer} already applies to JWT.
 */
public interface PushNotificationSender {

  void send(String pushToken, String title, String body);
}
