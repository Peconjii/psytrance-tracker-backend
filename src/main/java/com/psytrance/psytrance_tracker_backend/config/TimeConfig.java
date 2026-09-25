package com.psytrance.psytrance_tracker_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /** Injected instead of calling LocalDate.now() directly, so tests can pin "today" to a fixed date. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
