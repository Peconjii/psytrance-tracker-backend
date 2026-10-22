package com.psytrance.psytrance_tracker_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps bots and floods off the public forms without a captcha: each form allows so many sends per address or
 * account in a time window (registering, feedback, page claims, new page requests). The other half is the hidden
 * "website" field of those forms, which only bots fill in ({@link #isBot}). In memory: a restart forgets it, which
 * is fine for one server.
 */
@Service
public class FormLimits {

    private final Clock clock;

    // "form|key" -> when it was sent, within the form's window. The deques are only touched inside compute calls,
    // which the map runs one at a time per key.
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();
    // The longest window any form uses, so keys quiet for longer can be forgotten
    private volatile Duration longest = Duration.ZERO;

    public FormLimits(Clock clock) {
        this.clock = clock;
    }

    /** True when the hidden field people never see is filled in */
    public static boolean isBot(String hiddenField) {
        return hiddenField != null && !hiddenField.isBlank();
    }

    /**
     * Counts one send of {@code form} by {@code key} (an address or a username), or 429 with {@code message} when
     * there were {@code max} already within {@code window}.
     */
    public void count(String form, String key, int max, Duration window, String message) {
        Instant now = clock.instant();
        if (window.compareTo(longest) > 0) {
            longest = window;
        }
        Instant forgetBefore = now.minus(longest);
        // Keys quiet for the longest window are forgotten, so the map doesn't grow with every address that ever sent
        for (String k : recent.keySet()) {
            recent.computeIfPresent(k, (x, times) -> times.peekLast().isBefore(forgetBefore) ? null : times);
        }
        Instant windowStart = now.minus(window);
        boolean[] tooMany = {false};
        recent.compute(form + "|" + key, (k, times) -> {
            Deque<Instant> list = times == null ? new ArrayDeque<>() : times;
            while (!list.isEmpty() && list.peekFirst().isBefore(windowStart)) {
                list.pollFirst();
            }
            if (list.size() >= max) {
                tooMany[0] = true;
            } else {
                list.addLast(now);
            }
            return list.isEmpty() ? null : list;
        });
        if (tooMany[0]) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }
}
