package com.psytrance.psytrance_tracker_backend.util;

import com.psytrance.psytrance_tracker_backend.dto.LineUpLine;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    // What follows an artist name: "(Label)", "[DE]", " - live", " – Ecstatic Dance", " | Stage", " — Label"
    private static final Pattern AFTER_NAME = Pattern.compile("\\s*[(\\[]|\\s[-–—|┃/]\\s|\\s*[–—|┃]");
    // Bullets, arrows and emoji in front of the name
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+");
    // Flags, emoji and punctuation after the name
    private static final Pattern TRAILING_SYMBOLS = Pattern.compile("[^\\p{L}\\p{N}]+$");
    // "Aleph live", "Etnica DJ set"
    private static final Pattern PERFORMANCE_SUFFIX = Pattern.compile("(?i)\\s+(live|dj\\s*set|dj-set|live\\s*act)$");
    // Headers and notes around the names: "LINE UP", "MAIN FLOOR", "Special guest", "Visuals by ...", "Flyer design by ..."
    private static final Pattern HEADER = Pattern.compile("(?i).*(and more|special guest|line\\s*-?\\s*up|featuring"
            + "|\\bstage\\b|\\bfloor\\b|visuals|deco\\b|\\bdecor|\\bdeko|mapping|artwork|\\bflyer\\b|about (the )?event).*");
    // A slot without a name yet, or a word between names. Checked on the name only, because
    // "THE HORRIDS (TBC)" is still an artist
    private static final Pattern PLACEHOLDER = Pattern.compile("(?i)tba|tbc|tbd|to be announced|b2b|vs\\.?");
    // Section titles made only of genre words: "Darkpsy", "CHILL OUT", "Darkpsy/Forest", "Dark Prog"
    private static final Pattern GENRE_SEPARATORS = Pattern.compile("[\\s/&+,-]+");
    private static final Set<String> GENRE_WORDS = Set.of(
            "psy", "psytrance", "trance", "dark", "darkpsy", "forest", "psycore", "hitech", "hi", "tech",
            "goa", "progressive", "prog", "full", "on", "fullon", "downtempo", "chill", "out", "chillout",
            "ambient", "psychill", "psybient", "techno", "minimal", "twilight", "night", "morning",
            "experimental", "zenonesque", "house", "dub", "bass");

    private LineUpParser() {
    }

    public static List<LineUpLine> parse(String lineUp) {
        List<LineUpLine> lines = new ArrayList<>();
        if (lineUp == null) {
            return lines;
        }
        boolean previousWasBlank = true;
        // Goabase sends some characters HTML-escaped ("Remaza &gt; TrancerSpacy")
        for (String raw : HtmlUtils.htmlUnescape(lineUp).split("\\R")) {
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

    /**
     * The line-up split at its headers, so each artist can be tied to the stage they play on:
     * <pre>
     * DARKPSY / FOREST STAGE   -> header "DARKPSY / FOREST STAGE", artists [Derango, Megalopsy]
     * Derango
     * Megalopsy
     * </pre>
     * Artists before the first header get a null header. A header lasts until the next one; blank
     * lines don't end it, because organizers often leave a gap between the header and the names.
     */
    public static List<Section> sections(String lineUp) {
        List<Section> sections = new ArrayList<>();
        String header = null;
        List<String> artists = new ArrayList<>();
        for (LineUpLine line : parse(lineUp)) {
            if (line.artist() != null) {
                artists.add(line.artist());
            } else if (isHeader(line.text())) {
                if (header != null || !artists.isEmpty()) {
                    sections.add(new Section(header, List.copyOf(artists)));
                }
                header = line.text();
                artists = new ArrayList<>();
            }
        }
        if (header != null || !artists.isEmpty()) {
            sections.add(new Section(header, List.copyOf(artists)));
        }
        return sections;
    }

    /** One stage or part of a line-up: its header as written (null before the first header) and its artists. */
    public record Section(String header, List<String> artists) {
    }

    // "MAIN FLOOR | GOA // PSYTRANCE", "🌲 DARKPSY / FOREST", "Line up:" - but not sentences, links or "TBA"
    private static boolean isHeader(String line) {
        if (line.isEmpty() || line.length() > MAX_LINE_LENGTH || line.contains("http") || line.contains("www.")) {
            return false;
        }
        String words = TRAILING_SYMBOLS.matcher(LEADING_SYMBOLS.matcher(line).replaceFirst("")).replaceFirst("");
        return HEADER.matcher(line).matches() || line.endsWith(":") || (!words.isEmpty() && isOnlyGenres(words));
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
                || isOnlyGenres(name)
                || !containsLetter(name)) {
            return null;
        }
        return name;
    }

    private static boolean isOnlyGenres(String name) {
        return Arrays.stream(GENRE_SEPARATORS.split(name.toLowerCase(Locale.ROOT)))
                .allMatch(GENRE_WORDS::contains);
    }

    private static boolean containsLetter(String value) {
        return value.codePoints().anyMatch(Character::isLetter);
    }
}
