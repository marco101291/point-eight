package com.pointeight.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El dominio nunca llama a {@code Instant.now()} directamente: recibe un {@link Clock}. Eso permite
 * testear vencimientos con un reloj fijo, sin dormir el test.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
