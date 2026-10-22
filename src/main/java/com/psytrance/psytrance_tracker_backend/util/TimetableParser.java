package com.psytrance.psytrance_tracker_backend.util;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

/**
 * Reads set times from Goabase line-ups, where some organizers write them in front of the names:
 * <pre>
 * 21:00 Carbon Based Lifeforms
 * 23:00–00:30 Uhr — Zortarf
 * 7:00 PM – 8:00 PM — ACiDMAN 🇹🇭 @acidman420i
 * 22:00 - 00:00 | Naraka • Twilight/Forest
 * </pre>
 * A time earlier than the one before it is the next day (02:00 after 23:00), and a date line ("31.10.2026")
 * says which day the next times are on, as does a weekday before the time ("Sat 22:00 Ajja", "Samstag 22:00 Ajja").
 * Lines without a time or without an artist are left out.
 */
public final class TimetableParser {

    // Start time, optional end time, then the rest of the line: "23:00–00:30 Uhr — Zortarf", "7:00 PM – 8:00 PM — X"
    private static final Pattern TIMED = Pattern.compile("(?i)^[^\\p{L}\\p{N}]*"
            + "(\\d{1,2})[:h](\\d{2})\\s*(am|pm)?"
            + "(?:\\s*(?:-|–|—|to|bis)\\s*(\\d{1,2}|\\?\\?)(?:[:.h](\\d{2}|\\?\\?))?\\s*(am|pm)?)?"
            + "\\s*(?:uhr\\b)?\\s*[-–—|:•·]*\\s*(.*)$");
    // "Sat 22:00", "Saturday, 22:00", "Fr. 23:00", "Samstag 22:00": the weekday the set is on, in English or German.
    // Only right before a time, so a name like "Sunday Service" stays a name.
    private static final Pattern WEEKDAY = Pattern.compile("(?i)^[^\\p{L}\\p{N}]*"
            + "(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|mon|tue|wed|thu|fri|sat|sun|mo|di|mi|do|fr|sa|so)"
            + "(?:day|sday|nesday|rsday|urday)?\\.?,?\\s+(?=\\d{1,2}[:h]\\d{2})");
    private static final Map<String, DayOfWeek> DAYS = Map.ofEntries(
            Map.entry("mo", DayOfWeek.MONDAY), Map.entry("tu", DayOfWeek.TUESDAY), Map.entry("di", DayOfWeek.TUESDAY),
            Map.entry("we", DayOfWeek.WEDNESDAY), Map.entry("mi", DayOfWeek.WEDNESDAY), Map.entry("th", DayOfWeek.THURSDAY),
            Map.entry("do", DayOfWeek.THURSDAY), Map.entry("fr", DayOfWeek.FRIDAY), Map.entry("sa", DayOfWeek.SATURDAY),
            Map.entry("su", DayOfWeek.SUNDAY), Map.entry("so", DayOfWeek.SUNDAY));
    // "31.10.2026", "-23.10.2026-", "24/10": the times below it are on that day
    private static final Pattern DATE = Pattern.compile("(?:^|\\D)(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?(?:\\D|$)");
    // What follows the name in timetable lines: "@handle", "• Twilight/Forest"
    private static final Pattern AFTER_NAME = Pattern.compile("\\s+(@|•|·)\\s*.*$");
    // "02:15 – 04:15 Live M3dra": a live set by M3dra. Only after a set time: without one, "Live Earth" is a name
    private static final Pattern LIVE_SET = Pattern.compile("(?i)^live\\s+(?=\\S)");
    // Programme items that are not acts
    private static final Set<String> NOT_ACTS = Set.of("doors", "doors open", "intro", "outro", "opening", "closing",
            "opening ceremony", "closing ceremony", "break", "pause", "end", "ende", "soundcheck", "warm up", "warmup",
            "start", "chill", "afterparty", "after party");

    private TimetableParser() {
    }

    /**
     * A set: stage header it is under (or null), artist, start and (when written) end. {@code note} is what the line
     * says after the name ("• Twilight/Forest", "- Nano Rec - UK"), where organizers often name the set's genre.
     */
    public record Slot(String stage, String artist, OffsetDateTime startsAt, OffsetDateTime endsAt, String note) {

        public Slot(String stage, String artist, OffsetDateTime startsAt, OffsetDateTime endsAt) {
            this(stage, artist, startsAt, endsAt, null);
        }
    }

    /**
     * The line without its leading set time, for finding the artist in it: "21:00 - 22:00 | Naraka • Twilight" ->
     * "Naraka". Lines that don't start with a time come back as they are.
     */
    public static String withoutTime(String line) {
        Matcher m = TIMED.matcher(withoutWeekday(line.strip()));
        if (!m.matches() || Integer.parseInt(m.group(1)) > 24) {
            return line;
        }
        String name = LIVE_SET.matcher(AFTER_NAME.matcher(m.group(7)).replaceFirst("").strip()).replaceFirst("");
        // "20:00 DOORS" is a programme item, not an act
        return NOT_ACTS.contains(name.toLowerCase(Locale.ROOT)) ? "" : name;
    }

    /**
     * The timetable of a line-up. {@code firstDay} and {@code offset} are the event's start day and UTC offset
     * as Goabase gives them; {@code startHour} the hour the event starts, so a first set at 01:00 of an evening
     * event is the night after.
     */
    public static List<Slot> parse(String lineUp, LocalDate firstDay, int startHour, ZoneOffset offset) {
        List<Slot> slots = new ArrayList<>();
        if (lineUp == null || firstDay == null) {
            return slots;
        }
        // Walk the lines in order, so stage headers and date lines apply to the times below them.
        // A new stage starts its own night again.
        String stage = null;
        LocalDate day = firstDay;
        LocalDateTime previous = null;
        // Unescaped like LineUpParser.parse does, so "Tom &amp; Jerry" gets the same name and slug as in the line-up
        for (String raw : HtmlUtils.htmlUnescape(lineUp).split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            // A weekday written before the time says the day itself
            LocalDate named = null;
            Matcher weekday = WEEKDAY.matcher(line);
            if (weekday.find()) {
                named = firstDay.plusDays(Math.floorMod(dayOf(weekday.group(1)).getValue() - firstDay.getDayOfWeek().getValue(), 7));
                line = line.substring(weekday.end());
            }
            Matcher m = TIMED.matcher(line);
            if (!m.matches() || Integer.parseInt(m.group(1)) > 24) {
                LocalDate dated = dateOf(line, firstDay);
                if (dated != null) {
                    day = dated;
                    previous = null;
                } else if (LineUpParser.findArtist(line) == null && isStageHeader(line)) {
                    stage = STAGE_EDGES.matcher(line).replaceAll("");
                    previous = null;
                    day = firstDay;
                }
                continue;
            }
            String artist = artistOf(m.group(7));
            if (artist == null) {
                continue;
            }
            LocalTime start = time(m.group(1), m.group(2), m.group(3));
            if (start == null) {
                continue;
            }
            LocalDateTime startsAt = (named != null ? named : day).atTime(start);
            if (m.group(1).equals("24")) {
                startsAt = (named != null ? named : day).plusDays(1).atStartOfDay();
            }
            if (named != null) {
                // The organizer wrote the day, so no guessing whether it is the night after
            } else if (previous == null && start.getHour() < 12 && startHour >= 12 && day.equals(firstDay)) {
                // An evening event's first set at 01:00 is the night after
                startsAt = startsAt.plusDays(1);
            } else if (previous != null && startsAt.isBefore(previous.minusHours(6))) {
                startsAt = startsAt.plusDays(1);
            }
            day = startsAt.toLocalDate();
            previous = startsAt;

            LocalDateTime endsAt = null;
            LocalTime end = m.group(4) == null ? null
                    : time(m.group(4), m.group(5) == null ? "00" : m.group(5), m.group(6) != null ? m.group(6) : m.group(3));
            if (end != null) {
                endsAt = startsAt.toLocalDate().atTime(end);
                if ("24".equals(m.group(4))) {
                    endsAt = startsAt.toLocalDate().plusDays(1).atStartOfDay();
                } else if (!endsAt.isAfter(startsAt)) {
                    endsAt = endsAt.plusDays(1);
                }
            }
            String rest = m.group(7);
            int at = rest.indexOf(artist);
            String note = at < 0 ? null : rest.substring(at + artist.length()).strip();
            slots.add(new Slot(stage, artist, startsAt.atOffset(offset), endsAt == null ? null : endsAt.atOffset(offset),
                    note == null || note.isEmpty() ? null : note));
        }
        return slots;
    }

    // "Sat 22:00 Ajja" -> "22:00 Ajja"
    private static String withoutWeekday(String line) {
        Matcher weekday = WEEKDAY.matcher(line);
        return weekday.find() ? line.substring(weekday.end()) : line;
    }

    private static DayOfWeek dayOf(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        // Every form starts with the two letters of its day: "sat", "saturday", "samstag", "sa"
        return DAYS.get(lower.substring(0, 2));
    }

    private static String artistOf(String rest) {
        String name = LIVE_SET.matcher(AFTER_NAME.matcher(rest).replaceFirst("").strip()).replaceFirst("");
        if (name.isEmpty()) {
            return null;
        }
        String artist = LineUpParser.findArtist(name);
        if (artist == null || NOT_ACTS.contains(artist.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return artist;
    }

    // "Time Table:", "Line-up:", "Gratis Panels ab 18:30:" introduce a list; they don't name a stage
    private static final Pattern NOT_A_STAGE = Pattern.compile("(?i).*(time\\s*-?\\s*table|line\\s*-?\\s*up|program|\\d{1,2}[:.]\\d{2}).*");
    // Symbols and the colon around a stage name: "🔮 MAIN FLOOR:" -> "MAIN FLOOR"
    private static final Pattern STAGE_EDGES = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N})]+$");

    private static boolean isStageHeader(String line) {
        return line.length() <= 60 && !line.contains("http") && !NOT_A_STAGE.matcher(line).matches() && (line.toLowerCase(Locale.ROOT).matches(".*\\b(stage|floor|area|dome|tent|room)\\b.*")
                || line.endsWith(":"));
    }

    // 24h or 12h ("7:00 PM"); "??" or out of range is no time
    private static LocalTime time(String hours, String minutes, String amPm) {
        if (hours.contains("?") || minutes.contains("?")) {
            return null;
        }
        int h = Integer.parseInt(hours);
        int min = Integer.parseInt(minutes);
        if (amPm != null) {
            boolean pm = amPm.equalsIgnoreCase("pm");
            if (h < 1 || h > 12) {
                return null;
            }
            h = h % 12 + (pm ? 12 : 0);
        }
        if (h == 24 && min == 0) {
            return LocalTime.MIDNIGHT;
        }
        if (h > 23 || min > 59) {
            return null;
        }
        return LocalTime.of(h, min);
    }

    // A line that is only a date (maybe with a place): "31.10.2026 · PARKHOUSE GRAZ", "-24.10.2026-"
    private static LocalDate dateOf(String line, LocalDate firstDay) {
        if (line.length() > 60) {
            return null;
        }
        Matcher m = DATE.matcher(line);
        if (!m.find()) {
            return null;
        }
        int dayOfMonth = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        if (dayOfMonth < 1 || dayOfMonth > 31 || month < 1 || month > 12) {
            return null;
        }
        int year = firstDay.getYear();
        if (m.group(3) != null) {
            year = Integer.parseInt(m.group(3));
            year = year < 100 ? 2000 + year : year;
        }
        try {
            LocalDate date = LocalDate.of(year, month, dayOfMonth);
            // Only days of the event itself (a few days long at most)
            return !date.isBefore(firstDay) && !date.isAfter(firstDay.plusDays(10)) ? date : null;
        } catch (DateTimeException e) {
            return null;
        }
    }
}
