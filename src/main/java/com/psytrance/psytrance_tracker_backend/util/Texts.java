package com.psytrance.psytrance_tracker_backend.util;

/** Small text helpers the forms share */
public final class Texts {

    /** Blank fields are kept as no value (null), the rest without the spaces around them */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Text safe inside HTML or XML: & < > and " as entities (put it between tags or in "quoted" attributes) */
    public static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private Texts() {
    }
}
