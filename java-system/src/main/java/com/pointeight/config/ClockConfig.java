package com.pointeight.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The domain never calls {@code Instant.now()} directly: it receives a {@link Clock}. That lets
 * expirations be tested with a fixed clock, without sleeping the test.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
