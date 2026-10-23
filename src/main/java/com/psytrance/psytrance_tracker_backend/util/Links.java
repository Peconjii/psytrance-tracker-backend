package com.psytrance.psytrance_tracker_backend.util;

/** What a link field accepts: an https address without spaces; blank (clears it) where the field is optional */
public final class Links {

    public static final String HTTPS = "https://\\S+";
    public static final String HTTPS_OR_BLANK = "\\s*|https://\\S+";
    public static final String HTTPS_MESSAGE = "must be an https:// address";

    private Links() {
    }
}
