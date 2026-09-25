package com.psytrance.psytrance_tracker_backend.exception;

/**
 * Goabase could not be reached or answered with an error. Mapped to 503 in
 * {@link GlobalExceptionHandler}.
 */
public class GoabaseUnavailableException extends RuntimeException {

    public GoabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
