package com.pointeight.simulation.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** {@code RestClient} pointed at the Engine. Base URL in {@code pointeight.engine.base-url}. */
@Configuration
public class EngineRestClientConfig {

  @Bean
  RestClient engineRestClient(
      RestClient.Builder builder, @Value("${pointeight.engine.base-url}") String baseUrl) {
    return builder.baseUrl(baseUrl).build();
  }
}
