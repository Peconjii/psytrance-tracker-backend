package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.ArtistProfile;

/** An artist's photo, bio, links, booking address and approved display name; every field can be null */
public record ArtistProfileDto(String photoUrl, String bio, String soundcloudUrl, String bandcampUrl,
                               String instagramUrl, String websiteUrl, String bookingEmail, String displayName) {

    public static final ArtistProfileDto EMPTY = new ArtistProfileDto(null, null, null, null, null, null, null, null);

    public static ArtistProfileDto from(ArtistProfile profile) {
        return new ArtistProfileDto(profile.getPhotoUrl(), profile.getBio(), profile.getSoundcloudUrl(),
                profile.getBandcampUrl(), profile.getInstagramUrl(), profile.getWebsiteUrl(), profile.getBookingEmail(),
                profile.getDisplayName());
    }
}
