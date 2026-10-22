package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileRequest;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.ArtistProfile;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistProfileRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Artists' profiles (photo, bio, links, booking), written by the artist's owner or an admin (see ArtistEditing,
 * which checks who may). Every save is logged in admin_changes with the fields that were filled before and after.
 */
@Service
public class ArtistProfiles {

    private final ArtistProfileRepository profileRepository;
    private final ArtistRepository artistRepository;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public ArtistProfiles(ArtistProfileRepository profileRepository, ArtistRepository artistRepository,
                          AdminChangeRepository changeRepository, Clock clock) {
        this.profileRepository = profileRepository;
        this.artistRepository = artistRepository;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    /** Writes the whole profile (blank fields clear it). Only artists from the line-ups have a page to show it on. */
    @Transactional
    public ArtistProfileDto save(String slug, ArtistProfileRequest request, String editorUsername) {
        Artist artist = artistRepository.findBySlug(ArtistNames.slug(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No artist called " + slug));
        ArtistProfile profile = profileRepository.findById(artist.getSlug()).orElseGet(() -> new ArtistProfile(artist.getSlug()));
        String before = filledFields(profile);
        profile.update(request.photoUrl(), request.bio(), request.soundcloudUrl(), request.bandcampUrl(),
                request.instagramUrl(), request.websiteUrl(), request.bookingEmail(), clock.instant());
        profileRepository.save(profile);
        changeRepository.save(new AdminChange(editorUsername, AdminChange.ARTIST_PROFILE, artist.getName(),
                before, filledFields(profile), clock.instant()));
        return ArtistProfileDto.from(profile);
    }

    // What the history shows: which parts of the profile are filled in, e.g. "photo, bio, instagram"
    private static String filledFields(ArtistProfile profile) {
        List<String> filled = new ArrayList<>();
        if (profile.getPhotoUrl() != null) filled.add("photo");
        if (profile.getBio() != null) filled.add("bio");
        if (profile.getSoundcloudUrl() != null) filled.add("soundcloud");
        if (profile.getBandcampUrl() != null) filled.add("bandcamp");
        if (profile.getInstagramUrl() != null) filled.add("instagram");
        if (profile.getWebsiteUrl() != null) filled.add("website");
        if (profile.getBookingEmail() != null) filled.add("booking");
        return String.join(", ", filled);
    }
}
