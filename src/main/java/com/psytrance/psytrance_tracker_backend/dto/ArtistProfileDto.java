package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.ArtistProfile;

/** An artist's photo, bio, links and booking address; every field can be null */
public record ArtistProfileDto(String photoUrl, String bio, String soundcloudUrl, String bandcampUrl,
                               String instagramUrl, String websiteUrl, String bookingEmail) {

    public static ArtistProfileDto from(ArtistProfile profile) {
        return new ArtistProfileDto(profile.getPhotoUrl(), profile.getBio(), profile.getSoundcloudUrl(),
                profile.getBandcampUrl(), profile.getInstagramUrl(), profile.getWebsiteUrl(), profile.getBookingEmail());
    }
}
