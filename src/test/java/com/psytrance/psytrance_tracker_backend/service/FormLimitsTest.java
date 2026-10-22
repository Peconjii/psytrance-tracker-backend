package com.psytrance.psytrance_tracker_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormLimitsTest {

    // A clock the test moves forward by hand
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final FormLimits limits = new FormLimits(clock);

    private void send(String form, String key) {
        limits.count(form, key, 3, Duration.ofHours(1), "Too many");
    }

    @Test
    void theLimitHoldsForTheWindowThenOpensAgain() {
        send("register", "1.2.3.4");
        send("register", "1.2.3.4");
        send("register", "1.2.3.4");
        assertThatThrownBy(() -> send("register", "1.2.3.4"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");

        // Another address and another form have their own counts
        send("register", "5.6.7.8");
        send("claim", "1.2.3.4");

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        send("register", "1.2.3.4");
    }

    @Test
    void onlyAFilledHiddenFieldIsABot() {
        assertThat(FormLimits.isBot(null)).isFalse();
        assertThat(FormLimits.isBot("  ")).isFalse();
        assertThat(FormLimits.isBot("https://spam.example")).isTrue();
    }
}
