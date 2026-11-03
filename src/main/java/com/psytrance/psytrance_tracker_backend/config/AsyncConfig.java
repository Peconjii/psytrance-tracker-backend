package com.psytrance.psytrance_tracker_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Lets methods marked {@code @Async} run in the background (Spring's default task executor), so a slow mail server
 * never keeps a form waiting. Used by {@code PasswordResetMailer}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
