package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.util.Links;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** What an artist's owner (or an admin) writes on the profile besides the profile fields: tracks and festivals */
public final class ArtistListsRequest {

    /** Most tracks a profile shows */
    public static final int MAX_TRACKS = 6;
    /** Most festivals a profile lists */
    public static final int MAX_FESTIVALS = 20;

    private ArtistListsRequest() {
    }

    /** A track: its title and a link to listen (YouTube, SoundCloud, Bandcamp...) */
    public record Track(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 300) @Pattern(regexp = Links.HTTPS, message = Links.HTTPS_MESSAGE) String url) {
    }

    /** {@code PUT /api/artists/{slug}/tracks}: the whole list, in order */
    public record Tracks(@NotNull @Size(max = MAX_TRACKS) List<@Valid @NotNull Track> tracks) {
    }

    /** A festival played: its name, the year if known, and a link to a set or line-up if there is one */
    public record Festival(
            @NotBlank @Size(max = 200) String name,
            @Min(1965) @Max(2100) Integer year,
            @Size(max = 300) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String url) {
    }

    /** {@code PUT /api/artists/{slug}/festivals}: the whole list, in order */
    public record Festivals(@NotNull @Size(max = MAX_FESTIVALS) List<@Valid @NotNull Festival> festivals) {
    }
}
