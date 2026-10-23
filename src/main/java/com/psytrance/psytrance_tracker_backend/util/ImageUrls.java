package com.psytrance.psytrance_tracker_backend.util;

/**
 * What a picture field (photo, logo, flyer) accepts: a link to a picture anywhere on https, or a picture uploaded to
 * this site ({@code …/api/images/12}; on a local backend that address is http://localhost).
 */
public final class ImageUrls {

    public static final String PATTERN = "\\s*|https://\\S+|http://localhost(:\\d+)?/api/images/\\d+";
    public static final String MESSAGE = "must be an https:// address or an uploaded picture";

    private ImageUrls() {
    }
}
