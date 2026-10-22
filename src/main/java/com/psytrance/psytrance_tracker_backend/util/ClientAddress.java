package com.psytrance.psytrance_tracker_backend.util;

import jakarta.servlet.http.HttpServletRequest;

/** The address a request came from, for limits per address (see FormLimits) */
public final class ClientAddress {

    private ClientAddress() {
    }

    /**
     * Behind Render's proxy the address is the last one in X-Forwarded-For: the proxy adds the one it saw, while
     * the ones before it are whatever the client wrote and could be made up.
     */
    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].strip();
        }
        return request.getRemoteAddr();
    }
}
