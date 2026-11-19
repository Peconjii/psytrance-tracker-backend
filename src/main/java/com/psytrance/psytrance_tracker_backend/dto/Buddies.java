package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/**
 * Festival Buddies of one event. {@code visibleCount} (how many going people chose to be visible) is shown to everyone;
 * the list itself only to a logged-in user who is going and visible too, otherwise {@code buddies} is empty and
 * {@code reason} says what is missing: "login", "not-going" or "not-visible".
 */
public record Buddies(long visibleCount, boolean canSee, String reason, List<Buddy> buddies) {

    public record Buddy(String username, String country, String note, String instagram, boolean me) {
    }
}
