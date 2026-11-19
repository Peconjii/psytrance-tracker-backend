package com.psytrance.psytrance_tracker_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Request bodies of the crew endpoints */
public final class CrewRequests {

    private CrewRequests() {
    }

    public record NewCrew(@NotBlank @Size(max = 60) String name) {
    }

    public record NewItem(@NotBlank @Size(max = 60) String name) {
    }

    public record CampSpot(@Size(max = 140) String campSpot) {
    }

    public record Pick(@NotBlank @Size(max = 100) String artist, @NotNull LocalDateTime startsAt) {
    }
}
