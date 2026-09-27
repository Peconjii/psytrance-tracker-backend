package com.psytrance.psytrance_tracker_backend.exception;

/**
 * YouTube could not be reached, rejected the request or the daily quota is used up.
 * Never reaches the client: an aftermovie is a nice extra, so the page just goes without one.
 */
public class YouTubeUnavailableException extends RuntimeException {

    public YouTubeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
