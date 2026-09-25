package com.psytrance.psytrance_tracker_backend.exception;

/**
 * The reset link is unknown, already used or expired. Mapped to 400 in {@link GlobalExceptionHandler}.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("This reset link is invalid or has expired. Please request a new one.");
    }
}
