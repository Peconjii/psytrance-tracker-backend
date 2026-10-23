package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The numbers on the artist and label pages */
class StatsIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(994_000);
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    @Test
    void anArtistsGigsAreCountedByCountryAndYearAndTheirFollowersByMonth() throws Exception {
        String unique = uniqueLetters();
        String name = "Stats Act " + unique;
        String slug = "stats-act-" + unique;
        long past = NEXT_EVENT_ID.getAndIncrement();
        long next = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveLineUp(past, List.of(name), Instant.now());
        artistService.saveSummary(event(past, LocalDate.now().minusDays(40), "Portugal"));
        artistService.saveLineUp(next, List.of(name), Instant.now());
        artistService.saveSummary(event(next, LocalDate.now().plusDays(10), "Germany"));

        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(put("/api/me/follows/{slug}", slug).header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/artists/{slug}/stats", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gigs.total").value(2))
                .andExpect(jsonPath("$.gigs.upcoming").value(1))
                .andExpect(jsonPath("$.gigs.past").value(1))
                .andExpect(jsonPath("$.gigs.byCountry[*].key").value(hasItem("Portugal")))
                .andExpect(jsonPath("$.followers").value(1))
                .andExpect(jsonPath("$.followersByMonth[0].total").value(1));
        mockMvc.perform(get("/api/artists/{slug}/stats", "nobody-" + unique)).andExpect(status().isNotFound());
    }

    @Test
    void aLabelCountsItsCrewTogether() throws Exception {
        long next = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveLineUp(next, List.of("Shakta"), Instant.now()); // on Dragonfly Records in labels.json
        artistService.saveSummary(event(next, LocalDate.now().plusDays(15), "Hungary"));

        mockMvc.perform(get("/api/labels/stats").param("label", "Dragonfly Records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crew").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.gigs.upcoming").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.upcoming[*].eventId").value(hasItem((int) next)));
        mockMvc.perform(get("/api/labels/stats").param("label", "No Such Records")).andExpect(status().isNotFound());
    }

    @Test
    void aFestivalsEditionsAreTheEventsWithItsNameAsAWord() throws Exception {
        String festival = "fest" + uniqueLetters();
        long edition = NEXT_EVENT_ID.getAndIncrement();
        long other = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveLineUp(edition, List.of("Edition Act " + festival), Instant.now());
        artistService.saveSummary(named(edition, festival + " Festival 2026"));
        artistService.saveSummary(named(other, festival + "land Party"));

        mockMvc.perform(get("/api/festivals/stats").param("match", festival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editions.length()").value(1))
                .andExpect(jsonPath("$.editions[0].eventId").value((int) edition))
                .andExpect(jsonPath("$.editions[0].artists").value(1))
                .andExpect(jsonPath("$.topArtists[0].key").value("Edition Act " + festival));
        mockMvc.perform(get("/api/festivals/stats").param("match", "ab")).andExpect(status().isBadRequest());
    }

    @Test
    void theListOfLabelsAndALabelsPastGigs() throws Exception {
        // Liquid Soul is on Iboga Records' crew: a gig coming up and one that is over
        long next = NEXT_EVENT_ID.getAndIncrement();
        long over = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveLineUp(next, List.of("Liquid Soul"), Instant.now());
        artistService.saveSummary(event(next, LocalDate.now().plusDays(12), "Denmark"));
        artistService.saveLineUp(over, List.of("Liquid Soul"), Instant.now());
        artistService.saveSummary(event(over, LocalDate.now().minusDays(12), "Denmark"));

        mockMvc.perform(get("/api/labels/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.label == 'Iboga Records')].crew").value(org.hamcrest.Matchers.contains(23)))
                .andExpect(jsonPath("$[?(@.label == 'Iboga Records')].upcomingGigs").value(
                        org.hamcrest.Matchers.contains(greaterThanOrEqualTo(1))));
        mockMvc.perform(get("/api/labels/stats").param("label", "Iboga Records"))
                .andExpect(jsonPath("$.upcoming[*].eventId").value(hasItem((int) next)))
                .andExpect(jsonPath("$.past[*].eventId").value(hasItem((int) over)));
    }

    @Test
    void editorsSetEditionsTheNameDoesntShow() throws Exception {
        String festival = "fest" + uniqueLetters();
        long named = NEXT_EVENT_ID.getAndIncrement();
        long renamed = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveSummary(named(named, festival + " 2026"));
        artistService.saveSummary(named(renamed, "One Day in Budapest " + festival.toUpperCase() + "x"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "feedback_admin", "email": "feedback_admin@example.com", "password": "secret-password-123"}
                """));
        String admin = bearer(login("feedback_admin", "secret-password-123"));

        // The renamed one is an edition after all, the named one isn't (ozora: a festival of the map)
        mockMvc.perform(put("/api/admin/events/{id}/festival-edition", renamed).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"festivalId\": \"ozora\", \"edition\": true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/festivals/stats").param("match", festival).param("id", "ozora"))
                .andExpect(jsonPath("$.editions.length()").value(2))
                .andExpect(jsonPath("$.editions[?(@.eventId == %d)].linked".formatted(renamed)).value(true));
        mockMvc.perform(put("/api/admin/events/{id}/festival-edition", named).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"festivalId\": \"ozora\", \"edition\": false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/festivals/stats").param("match", festival).param("id", "ozora"))
                .andExpect(jsonPath("$.editions.length()").value(1))
                .andExpect(jsonPath("$.editions[0].eventId").value((int) renamed));
        // Back to the name for both: only the named one again
        for (long id : List.of(named, renamed)) {
            mockMvc.perform(put("/api/admin/events/{id}/festival-edition", id).header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"festivalId\": \"ozora\", \"edition\": null}"))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/festivals/stats").param("match", festival).param("id", "ozora"))
                .andExpect(jsonPath("$.editions.length()").value(1))
                .andExpect(jsonPath("$.editions[0].eventId").value((int) named));
        mockMvc.perform(put("/api/admin/events/{id}/festival-edition", named).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"festivalId\": \"no-such-festival\", \"edition\": true}"))
                .andExpect(status().isNotFound());
    }

    private static EventDto named(long id, String name) {
        return new EventDto(id, name, "Town", "Hungary", "Festival", "Scheduled", LocalDate.now().plusDays(30), null,
                null, null, 1.0, 1.0);
    }

    private static EventDto event(long id, LocalDate day, String country) {
        return new EventDto(id, "Stats Night " + id, "Town", country, "Club", "Scheduled", day, null, null, null, 1.0, 1.0);
    }
}
