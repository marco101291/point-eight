package com.pointeight.push.infrastructure;

import com.pointeight.push.domain.PushNotificationException;
import com.pointeight.push.domain.PushNotificationSender;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to Expo's push service (not Firebase/APNs directly — Expo fronts both, so the client only
 * ever deals in Expo push tokens, not platform-specific ones). No API key required for the basic
 * send call this uses; Expo's optional access-token auth is for higher rate limits, not something
 * this project's scale needs yet.
 */
@Component
public class ExpoPushNotificationSender implements PushNotificationSender {

  private static final Logger log = LoggerFactory.getLogger(ExpoPushNotificationSender.class);

  private final RestClient restClient;

  public ExpoPushNotificationSender(
      RestClient.Builder builder, @Value("${pointeight.push.expo-base-url}") String baseUrl) {
    this.restClient = builder.baseUrl(baseUrl).build();
  }

  @Override
  public void send(String pushToken, String title, String body) {
    try {
      // Expo answers 200 even for a malformed or dead token — delivery failures ("device not
      // registered", "invalid credentials") show up inside this body, not as an HTTP error, so
      // logging it is the only way to notice one during manual testing. Not parsed/acted on: a
      // stale token clearing itself out is exactly the kind of housekeeping this project's
      // "minimal, contained" auth scope (DEC-022/023) has consistently deferred elsewhere too.
      String response =
          restClient
              .post()
              .uri("/--/api/v2/push/send")
              .contentType(MediaType.APPLICATION_JSON)
              .body(List.of(new ExpoPushMessage(pushToken, title, body, "default")))
              .retrieve()
              .body(String.class);
      log.debug("Expo push response: {}", response);
    } catch (RestClientException e) {
      throw new PushNotificationException("Expo push service rejected the notification", e);
    }
  }

  private record ExpoPushMessage(String to, String title, String body, String sound) {}
}
