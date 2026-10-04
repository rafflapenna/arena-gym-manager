package com.gymmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * System UTC clock as a bean, injected where time needs to be mockable in tests
 * (e.g. {@code LoginAttemptService}'s progressive login throttling).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
