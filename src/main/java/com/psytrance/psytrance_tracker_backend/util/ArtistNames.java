package com.psytrance.psytrance_tracker_backend.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Organizers write the same artist in different ways: "ACE VENTURA", "Ace Ventura", "Ace-Ventura".
 * The slug is what they have in common, and it is also the artist's address: /artists/ace-ventura.
 */
public final class ArtistNames {

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");
    // Letters of any alphabet are kept, so "Космос" still gets a slug
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MAX_SLUG_LENGTH = 100;

    private ArtistNames() {
    }

    /** "Ace Ventura" → "ace-ventura", "Ëlement (live)" → "element-live". Empty when nothing is left. */
    public static String slug(String name) {
        if (name == null) {
            return "";
        }
        String plain = ACCENTS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        String slug = NOT_LETTER_OR_DIGIT.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll("-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > MAX_SLUG_LENGTH ? slug.substring(0, MAX_SLUG_LENGTH) : slug;
    }

    /**
     * Which spelling to show. Many line-ups are written in capitals, so a name in normal case
     * ("Ace Ventura") replaces an all-caps one ("ACE VENTURA"), but never the other way round.
     */
    public static String preferredName(String current, String candidate) {
        return isAllCaps(current) && !isAllCaps(candidate) ? candidate : current;
    }

    private static boolean isAllCaps(String name) {
        return !name.equals(name.toLowerCase(Locale.ROOT)) && name.equals(name.toUpperCase(Locale.ROOT));
    }
}
