package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.ArtistFollows;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The labels of the map the logged-in user follows; their artists' gigs join the followed gigs
 * ({@code GET /api/me/follows/gigs}). Everything here needs login (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/me/label-follows")
public class LabelFollowController {

    private final ArtistFollows artistFollows;

    public LabelFollowController(ArtistFollows artistFollows) {
        this.artistFollows = artistFollows;
    }

    @GetMapping
    public List<String> getLabelFollows(Authentication authentication) {
        return artistFollows.labelFollows(authentication.getName());
    }

    /** Example: {@code PUT /api/me/label-follows?label=TIP Records}. Following twice is fine. */
    @PutMapping
    public Map<String, String> follow(@RequestParam String label, Authentication authentication) {
        return Map.of("label", artistFollows.followLabel(authentication.getName(), label));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@RequestParam String label, Authentication authentication) {
        artistFollows.unfollowLabel(authentication.getName(), label);
    }
}
