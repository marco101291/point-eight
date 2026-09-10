package com.pointeight.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pointeight.push.domain.PushNotificationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExpoPushNotificationSenderTest {

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final ExpoPushNotificationSender sender =
      new ExpoPushNotificationSender(builder, "https://exp.host");

  @Test
  void posts_the_message_to_expos_send_endpoint() {
    server
        .expect(requestTo("https://exp.host/--/api/v2/push/send"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    """
                    [{"to":"ExponentPushToken[abc]","title":"0.8","body":"hola","sound":"default"}]
                    """))
        .andRespond(withSuccess("{\"data\":[{\"status\":\"ok\"}]}", MediaType.APPLICATION_JSON));

    sender.send("ExponentPushToken[abc]", "0.8", "hola");

    server.verify();
  }

  @Test
  void wraps_a_failed_call_in_a_domain_exception() {
    server
        .expect(requestTo("https://exp.host/--/api/v2/push/send"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> sender.send("ExponentPushToken[abc]", "0.8", "hola"))
        .isInstanceOf(PushNotificationException.class);
  }
}
