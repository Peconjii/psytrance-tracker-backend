package com.psytrance.psytrance_tracker_backend.util;

/** Small text helpers the forms share */
public final class Texts {

    /** Blank fields are kept as no value (null), the rest without the spaces around them */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Texts() {
    }
}
