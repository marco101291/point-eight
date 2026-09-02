package com.pointeight.simulation.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the exchange, queues, and bindings for the compatibility score flow. Both sides declare
 * the same topology independently (Java here, python-engine in {@code app/messaging.py}) — RabbitMQ
 * doesn't care which one runs first, but the names have to match exactly on both sides, since
 * nothing enforces that at compile time the way a shared type would.
 */
@Configuration
public class CompatibilityMessagingConfig {

  public static final String EXCHANGE = "pointeight.compatibility";
  public static final String REQUEST_QUEUE = "compatibility.score.requested";
  public static final String REQUEST_ROUTING_KEY = "score.requested";
  public static final String RESPONSE_QUEUE = "compatibility.score.computed";
  public static final String RESPONSE_ROUTING_KEY = "score.computed";

  @Bean
  public DirectExchange compatibilityExchange() {
    return new DirectExchange(EXCHANGE);
  }

  @Bean
  public Queue compatibilityScoreRequestedQueue() {
    return new Queue(REQUEST_QUEUE, true);
  }

  @Bean
  public Queue compatibilityScoreComputedQueue() {
    return new Queue(RESPONSE_QUEUE, true);
  }

  @Bean
  public Binding compatibilityScoreRequestedBinding(
      Queue compatibilityScoreRequestedQueue, DirectExchange compatibilityExchange) {
    return BindingBuilder.bind(compatibilityScoreRequestedQueue)
        .to(compatibilityExchange)
        .with(REQUEST_ROUTING_KEY);
  }

  @Bean
  public Binding compatibilityScoreComputedBinding(
      Queue compatibilityScoreComputedQueue, DirectExchange compatibilityExchange) {
    return BindingBuilder.bind(compatibilityScoreComputedQueue)
        .to(compatibilityExchange)
        .with(RESPONSE_ROUTING_KEY);
  }

  /**
   * Spring Boot auto-configures {@code RabbitTemplate} to use this converter once it exists as a
   * bean — without it, messages would go over the wire as Java serialization, which python-engine
   * can't read.
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
