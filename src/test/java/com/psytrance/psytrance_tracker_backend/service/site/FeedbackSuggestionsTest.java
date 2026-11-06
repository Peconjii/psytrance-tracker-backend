package com.psytrance.psytrance_tracker_backend.service.site;

import com.psytrance.psytrance_tracker_backend.model.Feedback;
import com.psytrance.psytrance_tracker_backend.service.map.MapGenres;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackSuggestionsTest {

    private final MapGenres genres = new MapGenres(List.of(
            new MapGenres.Genre("psyrock", "Psychedelic rock", "roots", List.of("Pink Floyd"))));

    private static Feedback feedback(Feedback.About about, String ref, String message) {
        return new Feedback(Feedback.Kind.CORRECTION, about, ref, "/?genre=psyrock", message, null, null, Instant.EPOCH);
    }

    @Test
    void readsATrackSuggestionWithItsYoutubeId() {
        var s = FeedbackSuggestions.of(feedback(Feedback.About.ARTIST, "Buckethead",
                "Track suggestion for Buckethead: \"Soothsayer\" (2006)\n"
                        + "Link: https://www.youtube.com/watch?v=adV8-_hgL4g&list=RDadV8-_hgL4g&start_radio=1"), genres);

        assertThat(s).contains(new FeedbackSuggestions.Suggestion("TRACK", "Buckethead", "Soothsayer", 2006, "adV8-_hgL4g", null));
    }

    @Test
    void aTrackWithoutYearOrYoutubeLinkStillCounts() {
        var s = FeedbackSuggestions.of(feedback(Feedback.About.ARTIST, "Gong",
                "Track suggestion for Gong: \"Flying Teapot\"\nLink: https://gong.bandcamp.com/track/x"), genres);

        assertThat(s).contains(new FeedbackSuggestions.Suggestion("TRACK", "Gong", "Flying Teapot", null, null, null));
    }

    @Test
    void readsAnArtistSuggestionByGenreName() {
        var s = FeedbackSuggestions.of(feedback(Feedback.About.GENRE, "Psychedelic rock",
                "Artist suggestion for Psychedelic rock: \"Jimi Hendrix\""), genres);

        assertThat(s).contains(new FeedbackSuggestions.Suggestion("ARTIST", "Jimi Hendrix", null, null, null, "psyrock"));
    }

    @Test
    void readsTheRightGenresOfAnEvent() {
        var s = FeedbackSuggestions.of(feedback(Feedback.About.EVENT, "Ozora Festival (#123)",
                "Event correction · genre: \"Psychedelic rock, Nonsense\"\nThey play rock"), genres);

        assertThat(s).contains(new FeedbackSuggestions.Suggestion("EVENT_GENRE", null, "123", null, null, "psyrock"));
    }

    @Test
    void ordinaryFeedbackIsNoSuggestion() {
        assertThat(FeedbackSuggestions.of(feedback(Feedback.About.ARTIST, "Ajja", "Wrong country"), genres)).isEmpty();
        assertThat(FeedbackSuggestions.genreOfPage("/?genre=psyrock&artist=X")).isEqualTo(Optional.of("psyrock"));
        assertThat(FeedbackSuggestions.youtubeId("Link: https://youtu.be/TLV4_xaYynY")).isEqualTo("TLV4_xaYynY");
    }
}
