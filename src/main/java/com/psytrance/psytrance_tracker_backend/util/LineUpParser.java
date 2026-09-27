package com.psytrance.psytrance_tracker_backend.util;

import com.psytrance.psytrance_tracker_backend.dto.LineUpLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds artist names in Goabase line-ups, which are free text written by organizers:
 * <pre>
 * 🔮 MAIN FLOOR | GOA // PSYTRANCE
 * DJ LUCAS (Tip Records)
 * INDRA – Ecstatic Dance
 * Earth M 🇸🇪
 * ... and more tba
 * </pre>
 * It is a best guess on purpose: a line it isn't sure about gets no artist, and the
 * page still shows the line as written.
 */
public final class LineUpParser {

    // Longer lines are sentences (descriptions, adverts), not "artist (label)"
    private static final int MAX_LINE_LENGTH = 60;
    private static final int MAX_NAME_LENGTH = 30;
    private static final int MAX_NAME_WORDS = 4;

    // What follows an artist name: "(Label)", " - live", " – Ecstatic Dance", " | Stage", " — Label"
    private static final Pattern AFTER_NAME = Pattern.compile("\\s*\\(|\\s[-–—|┃/]\\s|\\s*[–—|┃]");
    // Bullets, arrows and emoji in front of the name
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+");
    // Flags, emoji and punctuation after the name
    private static final Pattern TRAILING_SYMBOLS = Pattern.compile("[^\\p{L}\\p{N}]+$");
    // "Aleph live", "Etnica DJ set"
    private static final Pattern PERFORMANCE_SUFFIX = Pattern.compile("(?i)\\s+(live|dj\\s*set|dj-set|live\\s*act)$");
    // Headers and notes around the names: "LINE UP", "MAIN FLOOR", "Special guest", "Visuals by ..."
    private static final Pattern HEADER = Pattern.compile(
            "(?i).*(and more|special guest|line\\s*-?\\s*up|featuring|\\bstage\\b|\\bfloor\\b|visuals|deco\\b).*");
    // A slot without a name yet. Checked on the name only, because "THE HORRIDS (TBC)" is still an artist
    private static final Pattern PLACEHOLDER = Pattern.compile("(?i)tba|tbc|tbd|to be announced");

    private LineUpParser() {
    }

    public static List<LineUpLine> parse(String lineUp) {
        List<LineUpLine> lines = new ArrayList<>();
        if (lineUp == null) {
            return lines;
        }
        boolean previousWasBlank = true;
        for (String raw : lineUp.split("\\R")) {
            String text = raw.strip();
            // Keep blank lines as spacing between groups, but never two in a row
            if (text.isEmpty()) {
                if (!previousWasBlank) {
                    lines.add(new LineUpLine("", null));
                }
                previousWasBlank = true;
                continue;
            }
            lines.add(new LineUpLine(text, findArtist(text)));
            previousWasBlank = false;
        }
        int last = lines.size() - 1;
        if (last >= 0 && lines.get(last).text().isEmpty()) {
            lines.remove(last);
        }
        return lines;
    }

    static String findArtist(String line) {
        if (line.length() > MAX_LINE_LENGTH || line.contains("http") || line.contains("www.")
                || line.endsWith(":") || line.startsWith("(") || line.startsWith("...") || line.startsWith("+")
                || HEADER.matcher(line).matches()) {
            return null;
        }
        String name = LEADING_SYMBOLS.matcher(line).replaceFirst("");
        var after = AFTER_NAME.matcher(name);
        if (after.find() && after.start() > 0) {
            name = name.substring(0, after.start());
        }
        name = TRAILING_SYMBOLS.matcher(name).replaceFirst("");
        name = PERFORMANCE_SUFFIX.matcher(name).replaceFirst("");
        name = TRAILING_SYMBOLS.matcher(name).replaceFirst("").strip();

        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || name.split("\\s+").length > MAX_NAME_WORDS
                || name.contains(":")
                || PLACEHOLDER.matcher(name).matches()
                || !containsLetter(name)) {
            return null;
        }
        return name;
    }

    private static boolean containsLetter(String value) {
        return value.codePoints().anyMatch(Character::isLetter);
    }
}
