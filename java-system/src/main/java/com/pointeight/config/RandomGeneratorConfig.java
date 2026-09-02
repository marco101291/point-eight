package com.pointeight.config;

import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same idea as {@link ClockConfig}, for chance instead of time: nothing else in the codebase
 * reaches for {@code Math.random()} directly — this is the one place that constructs a generator,
 * so tests can inject a seeded {@link RandomGenerator} instead and get a reproducible pick.
 *
 * <p>Uses {@code new Random()}, not {@code RandomGenerator.getDefault()}: the latter picks its
 * algorithm via {@code ServiceLoader}, which fails at runtime inside Spring Boot's repackaged jar
 * ({@code IllegalArgumentException: No implementation of the random number generator algorithm
 * "L32X64MixRandom" is available}) — {@code java.util.Random} has implemented {@code
 * RandomGenerator} directly since Java 17, with no service lookup involved.
 */
@Configuration
public class RandomGeneratorConfig {

  @Bean
  public RandomGenerator randomGenerator() {
    return new Random();
  }
}
