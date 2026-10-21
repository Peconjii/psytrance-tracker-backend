package com.psytrance.psytrance_tracker_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops guessing passwords: after a few wrong passwords for one username, that username can't log in for a while.
 * Counted per username rather than per address, so guessing an admin's password from many addresses is slowed
 * down too. In memory: a restart forgets it, which is fine.
 */
@Service
public class LoginAttempts {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    // When each username last typed a wrong password, within the window. The deques are only touched inside
    // compute calls, which the map runs one at a time per username.
    private final Map<String, Deque<Instant>> failuresByUsername = new ConcurrentHashMap<>();

    public LoginAttempts(Clock clock,
                         @Value("${app.login.max-failures:10}") int maxFailures,
                         @Value("${app.login.lockout:PT15M}") Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /** 429 when this username had too many wrong passwords lately; call before checking the password. */
    public void checkAllowed(String username) {
        Deque<Instant> failures = failuresByUsername.computeIfPresent(key(username), (k, times) -> recent(times));
        if (failures != null && failures.size() >= maxFailures) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong passwords. Try again in " + window.toMinutes() + " minutes, or reset your password.");
        }
    }

    public void failed(String username) {
        Instant now = clock.instant();
        // Usernames quiet for the whole window are forgotten, so the map doesn't grow with every name ever tried
        for (String key : failuresByUsername.keySet()) {
            failuresByUsername.computeIfPresent(key, (k, times) -> recent(times));
        }
        failuresByUsername.compute(key(username), (k, times) -> {
            Deque<Instant> kept = times == null ? new ArrayDeque<>() : times;
            kept.addLast(now);
            return kept;
        });
    }

    public void succeeded(String username) {
        failuresByUsername.remove(key(username));
    }

    // Only the failures inside the window; null (the entry is removed) when none are left
    private Deque<Instant> recent(Deque<Instant> times) {
        Instant windowStart = clock.instant().minus(window);
        while (!times.isEmpty() && times.peekFirst().isBefore(windowStart)) {
            times.pollFirst();
        }
        return times.isEmpty() ? null : times;
    }

    private static String key(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
