package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.model.FollowNotice;
import com.psytrance.psytrance_tracker_backend.repository.FollowNoticeRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.service.ArtistFollows;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Following a label of the map: the gigs of its artists count as followed gigs. */
class LabelFollowIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(995_000);
    private static final String PASSWORD = "secret-password-123";
    // A label of map/labels.json and an artist on it
    private static final String LABEL = "Dragonfly Records";
    private static final String ON_THE_LABEL = "Shakta";

    @Autowired
    private ArtistService artistService;

    @Autowired
    private ArtistFollows artistFollows;

    @Autowired
    private FollowNoticeRepository noticeRepository;

    @Autowired
    private UserRepository userRepository;

    private long firstGig;
    private long secondGig;

    @BeforeEach
    void anArtistOfTheLabelWithOneGig() {
        firstGig = NEXT_EVENT_ID.getAndIncrement();
        secondGig = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(party(firstGig, 6), party(secondGig, 14)));
        artistService.saveLineUp(firstGig, List.of(ON_THE_LABEL), Instant.now());
    }

    @Test
    void theLabelsArtistsGigsAreFollowed() throws Exception {
        String token = newUser();
        mockMvc.perform(put("/api/me/label-follows").param("label", LABEL).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value(LABEL));
        mockMvc.perform(get("/api/me/label-follows").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(LABEL));
        mockMvc.perform(get("/api/me/follows/gigs").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[?(@.eventId == %d)].artists[0]".formatted(firstGig)).value(hasItem(ON_THE_LABEL)));

        mockMvc.perform(delete("/api/me/label-follows").param("label", LABEL).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/follows/gigs").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyNewGigsOfTheLabelAreEmailed() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        long userId = userRepository.findByUsername(username).orElseThrow().getId();
        mockMvc.perform(put("/api/me/label-follows").param("label", LABEL).header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(status().isOk());

        artistFollows.sendNewGigEmails();
        assertThat(noticedEvents(userId)).contains(firstGig).doesNotContain(secondGig);

        artistService.saveLineUp(secondGig, List.of(ON_THE_LABEL, "Someone Else"), Instant.now());
        artistFollows.sendNewGigEmails();
        assertThat(noticedEvents(userId)).contains(firstGig, secondGig);
    }

    @Test
    void onlyLabelsOfTheMapCanBeFollowed() throws Exception {
        mockMvc.perform(put("/api/me/label-follows").param("label", LABEL))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/label-follows").param("label", "No Such Records").header("Authorization", bearer(newUser())))
                .andExpect(status().isNotFound());
    }

    private List<Long> noticedEvents(long userId) {
        return noticeRepository.findByUserId(userId).stream().map(FollowNotice::getEventId).toList();
    }

    private String newUser() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        return login(username, PASSWORD);
    }

    private static GoabaseParty party(long id, int daysFromNow) {
        return new GoabaseParty(id, "Label Fest " + id, LocalDate.now().plusDays(daysFromNow) + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Portugal", "Idanha-a-Nova", 39.9, -7.2, null, null);
    }
}
