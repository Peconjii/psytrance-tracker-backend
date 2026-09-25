package com.psytrance.psytrance_tracker_backend.exception;

/**
 * No event with the requested id exists. Mapped to 404 in {@link GlobalExceptionHandler}.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(long id) {
        super("Event " + id + " not found");
    }
}
