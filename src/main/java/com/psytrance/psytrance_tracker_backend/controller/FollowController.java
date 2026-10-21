package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.FollowDto;
import com.psytrance.psytrance_tracker_backend.dto.FollowedGig;
import com.psytrance.psytrance_tracker_backend.service.ArtistFollows;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The artists the logged-in user follows. Everything here needs login (see SecurityConfig). */
@RestController
@RequestMapping("/api/me/follows")
public class FollowController {

    private final ArtistFollows artistFollows;

    public FollowController(ArtistFollows artistFollows) {
        this.artistFollows = artistFollows;
    }

    @GetMapping
    public List<FollowDto> getFollows(Authentication authentication) {
        return artistFollows.follows(authentication.getName());
    }

    /** Example: {@code PUT /api/me/follows/ace-ventura}. Following twice is fine. */
    @PutMapping("/{slug}")
    public FollowDto follow(@PathVariable String slug, Authentication authentication) {
        return artistFollows.follow(authentication.getName(), slug);
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String slug, Authentication authentication) {
        artistFollows.unfollow(authentication.getName(), slug);
    }

    /** Upcoming events where followed artists play, for the gold ships on the map */
    @GetMapping("/gigs")
    public List<FollowedGig> getGigs(Authentication authentication) {
        return artistFollows.gigs(authentication.getName());
    }
}
