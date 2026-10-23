package com.psytrance.psytrance_tracker_backend.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Who is asking, on the endpoints guests may call too */
public final class Logins {

    /** The logged-in username, or null for a guest */
    public static String usernameOrNull(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken ? null : authentication.getName();
    }

    private Logins() {
    }
}
