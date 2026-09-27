package com.psytrance.psytrance_tracker_backend.dto;

/** A festival's aftermovie on YouTube; the frontend embeds it by videoId. */
public record AftermovieDto(String videoId, String title) {
}
