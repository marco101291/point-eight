package com.pointeight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} — needed for {@code MatchExpiryScheduler}. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
