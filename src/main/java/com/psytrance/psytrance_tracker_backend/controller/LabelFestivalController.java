package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.util.Links;
import com.psytrance.psytrance_tracker_backend.util.ImageUrls;
import com.psytrance.psytrance_tracker_backend.service.FestivalPages;
import com.psytrance.psytrance_tracker_backend.service.LabelPages;
import com.psytrance.psytrance_tracker_backend.service.OwnerStats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.psytrance.psytrance_tracker_backend.util.Logins.usernameOrNull;

/**
 * Pages of the map's labels and festivals, written by their owners (an admin links them). GET is public in
 * SecurityConfig, so the edit endpoints check the login themselves (401 for guests, 403 for other users). Label names
 * have spaces, so they go in the query: {@code ?label=TIP Records}; festivals by the map's id: {@code ?id=ozora}.
 */
@RestController
public class LabelFestivalController {

    private final LabelPages labelPages;
    private final FestivalPages festivalPages;
    private final OwnerStats ownerStats;

    public LabelFestivalController(LabelPages labelPages, FestivalPages festivalPages, OwnerStats ownerStats) {
        this.labelPages = labelPages;
        this.festivalPages = festivalPages;
        this.ownerStats = ownerStats;
    }

    public record LabelProfileRequest(
            @Size(max = 2000) String description,
            @Size(max = 500) @Pattern(regexp = ImageUrls.PATTERN, message = ImageUrls.MESSAGE) String logoUrl,
            @Size(max = 300) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String websiteUrl,
            @Size(max = 300) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String bandcampUrl,
            @Size(max = 300) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String instagramUrl) {
    }

    /** ADD an artist from the line-ups to the crew, or REMOVE one; an admin decides (an admin's own is done at once) */
    public record CrewRequest(@NotBlank String kind, @NotBlank @Size(max = 100) String artistName) {
    }

    public record FestivalProfileRequest(
            @Size(max = 2000) String description,
            @Size(max = 500) @Pattern(regexp = ImageUrls.PATTERN, message = ImageUrls.MESSAGE) String logoUrl,
            @Size(max = 300) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String websiteUrl,
            @Size(max = 300) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String ticketUrl,
            @Size(max = 300) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String instagramUrl,
            @Size(max = 100) String organizerSlug) {
    }

    // ---- Labels ----

    @GetMapping("/api/labels/page")
    public LabelPages.Page labelPage(@RequestParam String label) {
        return labelPages.page(label);
    }

    @GetMapping("/api/labels/edit")
    public LabelPages.Edit labelEdit(@RequestParam String label, Authentication authentication) {
        return labelPages.edit(label, usernameOrNull(authentication));
    }

    @PutMapping("/api/labels/profile")
    public LabelPages.Profile labelProfile(@RequestParam String label, @Valid @RequestBody LabelProfileRequest request,
                                           Authentication authentication) {
        return labelPages.saveProfile(label, new LabelPages.Profile(request.description(), request.logoUrl(),
                request.websiteUrl(), request.bandcampUrl(), request.instagramUrl()), usernameOrNull(authentication));
    }

    /** Returns the crew as it is now (changed already only when an admin asked) */
    @PostMapping("/api/labels/requests")
    public List<String> labelCrewRequest(@RequestParam String label, @Valid @RequestBody CrewRequest request,
                                         Authentication authentication) {
        return labelPages.requestCrew(label, request.kind(), request.artistName(), usernameOrNull(authentication));
    }

    @GetMapping("/api/labels/owner-stats")
    public OwnerStats.LabelNumbers labelOwnerStats(@RequestParam String label, Authentication authentication) {
        return ownerStats.label(label, usernameOrNull(authentication));
    }

    // ---- Festivals ----

    @GetMapping("/api/festivals/page")
    public FestivalPages.Page festivalPage(@RequestParam String id) {
        return festivalPages.page(id);
    }

    @GetMapping("/api/festivals/edit")
    public FestivalPages.Edit festivalEdit(@RequestParam String id, Authentication authentication) {
        return festivalPages.edit(id, usernameOrNull(authentication));
    }

    @PutMapping("/api/festivals/profile")
    public FestivalPages.Page festivalProfile(@RequestParam String id, @Valid @RequestBody FestivalProfileRequest request,
                                              Authentication authentication) {
        return festivalPages.saveProfile(id, new FestivalPages.Profile(request.description(), request.logoUrl(), request.websiteUrl(),
                request.ticketUrl(), request.instagramUrl(), request.organizerSlug()), usernameOrNull(authentication));
    }

    @GetMapping("/api/festivals/owner-stats")
    public OwnerStats.FestivalNumbers festivalOwnerStats(@RequestParam String id, Authentication authentication) {
        return ownerStats.festival(id, usernameOrNull(authentication));
    }
}
