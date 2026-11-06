package com.psytrance.psytrance_tracker_backend.service.site;

import com.psytrance.psytrance_tracker_backend.model.Feedback;
import com.psytrance.psytrance_tracker_backend.service.map.MapGenres;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the "+ Suggest a track" and "+ Suggest an artist" messages of the feedback form (Feedback.jsx writes them
 * in a fixed shape) back into their parts, so the inbox can accept one with a click.
 * <pre>
 * Track suggestion for Buckethead: "Soothsayer" (2006)
 * Link: https://www.youtube.com/watch?v=adV8-_hgL4g&list=...
 *
 * Artist suggestion for Psychedelic rock: "Jimi Hendrix"
 * </pre>
 */
public final class FeedbackSuggestions {

    /**
     * {@code type}: TRACK or ARTIST. A track: {@code artist}, {@code title}, {@code year} and {@code youtube} (an id,
     * null without a YouTube link). An artist: {@code artist} and {@code genreId}, the genre it was suggested for.
     * EVENT_GENRE, the right genres of an event: {@code title} is the event id, {@code genreId} the ids, comma separated.
     */
    public record Suggestion(String type, String artist, String title, Integer year, String youtube, String genreId) {
    }

    private static final Pattern TRACK = Pattern.compile("^Track suggestion for (.+?): \"(.+)\"(?: \\((\\d{4})\\))?\\s*$", Pattern.MULTILINE);
    private static final Pattern ARTIST = Pattern.compile("^Artist suggestion for (.+?): \"(.+)\"\\s*$", Pattern.MULTILINE);
    // "Event correction · genre: "Forest, Dark psy"" about "<event name> (#123)"
    private static final Pattern EVENT_GENRE = Pattern.compile("^Event correction · genre: \"(.+)\"\\s*$", Pattern.MULTILINE);
    private static final Pattern EVENT_ID = Pattern.compile("#(\\d+)\\)$");
    private static final Pattern LINK =Pattern.compile("^Link: (\\S+)", Pattern.MULTILINE);
    // watch?v=ID, youtu.be/ID, /shorts/ID, /embed/ID; ids are 11 characters
    private static final Pattern YOUTUBE = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private FeedbackSuggestions() {
    }

    public static Optional<Suggestion> of(Feedback feedback, MapGenres genres) {
        String message = feedback.getMessage() == null ? "" : feedback.getMessage();
        if (feedback.getAboutType() == Feedback.About.ARTIST) {
            Matcher m = TRACK.matcher(message);
            if (m.find()) {
                return Optional.of(new Suggestion("TRACK", m.group(1).strip(), m.group(2).strip(),
                        m.group(3) == null ? null : Integer.valueOf(m.group(3)), youtubeId(message), null));
            }
        }
        if (feedback.getAboutType() == Feedback.About.EVENT) {
            Matcher m = EVENT_GENRE.matcher(message);
            Matcher id = EVENT_ID.matcher(feedback.getAboutRef() == null ? "" : feedback.getAboutRef());
            if (m.find() && id.find()) {
                List<String> genreIds = java.util.Arrays.stream(m.group(1).split(","))
                        .map(String::strip)
                        .map(name -> genres.all().stream()
                                .filter(g -> g.name().equalsIgnoreCase(name) || g.id().equalsIgnoreCase(name))
                                .map(MapGenres.Genre::id).findFirst().orElse(null))
                        .filter(java.util.Objects::nonNull).distinct().toList();
                if (!genreIds.isEmpty()) {
                    return Optional.of(new Suggestion("EVENT_GENRE", null, id.group(1), null, null, String.join(",", genreIds)));
                }
            }
        }
        if (feedback.getAboutType() == Feedback.About.GENRE) {
            Matcher m = ARTIST.matcher(message);
            if (m.find()) {
                String genreName = m.group(1).strip();
                Optional<String> genreId = genres.all().stream()
                        .filter(g -> g.name().equalsIgnoreCase(genreName) || g.id().equalsIgnoreCase(genreName))
                        .map(MapGenres.Genre::id).findFirst();
                if (genreId.isPresent()) {
                    return Optional.of(new Suggestion("ARTIST", m.group(2).strip(), null, null, null, genreId.get()));
                }
            }
        }
        return Optional.empty();
    }

    /** The YouTube video id of the message's "Link:" line, if it is a YouTube link */
    static String youtubeId(String message) {
        Matcher link = LINK.matcher(message);
        if (!link.find()) {
            return null;
        }
        Matcher id = YOUTUBE.matcher(link.group(1));
        return id.find() ? id.group(1) : null;
    }

    /** The genre a page address opens on the galaxy ("/?genre=psyrock&artist=..."), if any */
    static Optional<String> genreOfPage(String pageUrl) {
        if (pageUrl == null) {
            return Optional.empty();
        }
        Matcher m = Pattern.compile("[?&]genre=([A-Za-z0-9_-]+)").matcher(pageUrl);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

}
