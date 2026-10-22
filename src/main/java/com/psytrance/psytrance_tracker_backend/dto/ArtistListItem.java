package com.psytrance.psytrance_tracker_backend.dto;

/**
 * One artist in the artists list: upcoming gig count, the role they picked (null: not picked) and the share of
 * their gigs the line-ups wrote "live" for, so the site can guess a role for those who didn't pick one.
 */
public record ArtistListItem(String slug, String name, Long upcomingEvents, String role, double liveShare) {
}
