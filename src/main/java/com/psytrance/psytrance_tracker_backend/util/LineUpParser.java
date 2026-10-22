package com.psytrance.psytrance_tracker_backend.util;

import com.psytrance.psytrance_tracker_backend.dto.LineUpLine;
import org.springframework.web.util.HtmlUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
    // Bullets, arrows, emoji and marks like the Japanese "ー" in front of the name
    private static final Pattern LEADING_SYMBOLS = Pattern.compile("^(?:[^\\p{L}\\p{N}]|\\p{Lm})+");
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

    // --- Lines that look like a name but aren't an artist (all seen in real Goabase line-ups) ---

    // Whole lines that are words about the party: "Location", "Coming Soon", "DJs", "Food & Bar", "CH"
    private static final Set<String> NOT_ARTISTS = Set.of(
            "location", "lounge", "club", "coming soon", "details coming soon", "details comming soon", "live", "vj",
            "dj", "djs", "dj's", "dj set", "djset", "dj sets", "dj's tba", "djs tba", "artist", "artists", "camping",
            "food", "food & bar", "food & drinks", "bar", "children's area", "kids area", "deco", "decoration",
            "dress code", "free entry", "free party", "free rave", "artisan market", "art materials", "bodypainting by",
            "fire performance", "fire show", "face design", "facedesign", "electronic music", "tickets", "ticket", "info",
            "infos", "line up", "lineup", "residents", "workshops", "yoga", "market", "parking", "more", "and many more",
            "and many moore", "folgt", "ch", "de", "en", "es", "it", "fr", "hu", "at", "nl", "uk", "us", "br", "pt", "pl", "cz");
    // Headers of the parts of an event text that aren't the line-up: what follows them is skipped
    // until the next stage header. "Location:", "📍 Anfahrt", "Tickets", "Food & Drinks"
    private static final Pattern NOT_LINE_UP_HEADER = Pattern.compile("(?i)location|venue|address|adresse|anfahrt|directions"
            + "|how to get there|getting there|tickets?|entry|eintritt|einlass|abendkasse|prices?|preise?|food( & drinks?)?"
            + "|camping|workshops?|market|parking|shuttle|contact|kontakt|infos?|information|rules|regeln|awareness");
    // A year or a day with a month: "19. Dezember 2026", "28 NOVEMBRE", "30 OCT", "31.10.2026"
    private static final Pattern DATE = Pattern.compile("(?i).*\\b(19|20)\\d{2}\\b.*"
            + "|^\\d{1,2}\\.?\\s*(jan|feb|mar|apr|may|mai|jun|jul|aug|sep|oct|okt|nov|dec|dez|gen|giu|lug|ago|set|ott|dic)\\p{L}*\\b.*");
    // A number with a unit: "15min Pause", "1 hour from Dublin", "700 Meter Fussweg", "2 DANCE FLOORS", "Ab 18 Jahren"
    private static final Pattern MEASURE = Pattern.compile("(?i).*\\b\\d+\\s*(min|mins|minutes?|h|hours?|stunden?|uhr|km|m|meter|kw"
            + "|floors?|dance\\s*floors?|rooms?|stages?|partys|parties|days?|nights?|tage|jahren?|years?)\\b.*");
    // A street with a number: "Oberfeldstrasse 12A", "C/ Fermín Caballero, 69"
    private static final Pattern STREET = Pattern.compile("(?i)(^c/.*|.*(straße|strasse|str\\.|street|\\bst\\.|road|\\brd\\b|\\bweg\\b"
            + "|gasse|platz|allee|calle|\\bvia\\b|\\brue\\b|avenue|\\bave\\b|ulica|utca).*\\d+.*)");
    // A postcode and a town: "20359 Hamburg" always, "8302 Kloten" right after a street (a 4-digit one alone
    // could be an artist like "1200 Micrograms")
    private static final Pattern POSTCODE_5 = Pattern.compile("^\\d{5}\\s+\\p{L}[\\p{L} -]*$");
    private static final Pattern POSTCODE_4 = Pattern.compile("^\\d{4}\\s+\\p{L}[\\p{L} -]*$");
    // A link without http: "facebook.com/rene.ragas", "example.de"
    private static final Pattern DOMAIN = Pattern.compile("(?i).*\\b[\\w-]+\\.(com|de|net|org|at|ch|io|hu|it|es|fr|pl|ru|be|nl|uk|eu|info|me)\\b.*");
    // Promoters and labels, not artists: "COSMIC CIRCUS presents", "Braingames Records", "Aajaadee Rec"
    private static final Pattern PROMO = Pattern.compile("(?i).*\\b(presents?|präsentiert|presenta|records|recordings|rec)\\b.*");
    // Prices and door info: "Earlybird 10", "Abendkasse vorhanden", "Presale"
    private static final Pattern DOOR = Pattern.compile("(?i)^(earlybird|early bird|abendkass\\p{L}*|vorverkauf|presale|pre-sale|einlass|eintritt|entry|ab \\d+)\\b.*");
    // Sentences: "Come early. Stay late", "ATTENTION! Limited capacity", "camping, food, and community".
    // An initial is fine ("B. RYDER"), and so is a "!" inside a name ("M!GuZ")
    private static final Pattern SENTENCE = Pattern.compile(".*([!?](\\s|$)|\\p{L}{3,}\\.\\s|,\\s).*");

    private LineUpParser() {
    }

    public static List<LineUpLine> parse(String lineUp) {
        List<LineUpLine> lines = new ArrayList<>();
        if (lineUp == null) {
            return lines;
        }
        boolean previousWasBlank = true;
        // Under "Location:", "Tickets" and the like no line is an artist, until the next stage header
        boolean inLineUp = true;
        boolean afterStreet = false;
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
            String core = TRAILING_SYMBOLS.matcher(LEADING_SYMBOLS.matcher(text).replaceFirst("")).replaceFirst("");
            if (NOT_LINE_UP_HEADER.matcher(core).matches()) {
                inLineUp = false;
            } else if (isHeader(text)) {
                inLineUp = true;
            }
            String artist = inLineUp ? findArtist(text) : null;
            if (artist != null && afterStreet && POSTCODE_4.matcher(artist).matches()) {
                artist = null;
            }
            afterStreet = STREET.matcher(text).matches();
            lines.add(new LineUpLine(text, artist));
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
    /** The artists of the line-up written with "live" ("Astrix live", "Live M3dra", "Aleph (live act)"), by slug */
    public static Set<String> liveArtistSlugs(String lineUp) {
        Set<String> live = new HashSet<>();
        for (LineUpLine line : parse(lineUp)) {
            if (line.artist() != null && LIVE_WORD.matcher(line.text()).find()) {
                live.add(ArtistNames.slug(line.artist()));
            }
        }
        return live;
    }

    private static final Pattern LIVE_WORD = Pattern.compile("(?i)\\blive\\b");

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
        // "21:00 - 22:00 | Naraka": the name after a set time (see TimetableParser)
        line = TimetableParser.withoutTime(line);
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
        // Styled letters some organizers paste in ("𝗔𝗚𝗡𝗘𝗧𝗢𝗡") become plain ones, so the same artist gets the same slug
        name = Normalizer.normalize(name, Normalizer.Form.NFKC);
        // Blank characters that count as letters ("ᅠ", the Hangul filler some organizers use as a spacer) are not a name
        name = INVISIBLE.matcher(name).replaceAll("").strip();

        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || name.split("\\s+").length > MAX_NAME_WORDS
                || name.contains(":")
                || PLACEHOLDER.matcher(name).matches()
                || isOnlyGenres(name)
                || !containsLetter(name)
                || isNotAnArtist(line, name)) {
            return null;
        }
        return name;
    }

    private static boolean isNotAnArtist(String line, String name) {
        return NOT_ARTISTS.contains(name.toLowerCase(Locale.ROOT))
                || NOT_LINE_UP_HEADER.matcher(name).matches()
                || DATE.matcher(line).matches()
                || MEASURE.matcher(line).matches()
                || STREET.matcher(line).matches()
                || POSTCODE_5.matcher(name).matches()
                || DOMAIN.matcher(line).matches()
                || PROMO.matcher(name).matches()
                || DOOR.matcher(name).matches()
                || SENTENCE.matcher(name).matches();
    }

    private static boolean isOnlyGenres(String name) {
        return Arrays.stream(GENRE_SEPARATORS.split(name.toLowerCase(Locale.ROOT)))
                .allMatch(GENRE_WORDS::contains);
    }

    // Hangul fillers, the blank Braille pattern and zero-width characters
    private static final Pattern INVISIBLE = Pattern.compile("[\\u115F\\u1160\\u3164\\uFFA0\\u2800\\u200B-\\u200D\\u2060\\uFEFF]");

    private static boolean containsLetter(String value) {
        return value.codePoints().anyMatch(Character::isLetter);
    }
}
