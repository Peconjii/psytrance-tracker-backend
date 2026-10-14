package com.psytrance.psytrance_tracker_backend.dto;

import jakarta.validation.constraints.NotNull;

/** Whether the user gets reminder emails before their events. */
public record ReminderSettings(@NotNull Boolean enabled) {
}
