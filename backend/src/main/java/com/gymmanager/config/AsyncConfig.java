package com.gymmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} method execution (see {@link com.gymmanager.service.MailerService}).
 * Uses Spring Boot's auto-configured {@code applicationTaskExecutor} — no custom pool needed
 * for the current fire-and-forget email volume.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
