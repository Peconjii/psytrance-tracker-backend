package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import com.psytrance.psytrance_tracker_backend.service.WeeklyDigest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Monday email: the next two weeks of the followed artists and of the followed labels' crews */
class WeeklyDigestIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeeklyDigest weeklyDigest;

    @Test
    void followersGetTheNextGigsOncePerDayAndCanTurnItOff() throws Exception {
        String unique = uniqueLetters();
        long followedGig = 999_100L + (long) (Math.random() * 300);
        long labelGig = followedGig + 400;
        long farAway = followedGig + 800;
        String artist = "Digest Act " + unique;
        artistService.saveLineUp(followedGig, List.of(artist), Instant.now());
        artistService.saveSummary(event(followedGig, "Digest Night " + unique, LocalDate.now().plusDays(4)));
        // Liquid Soul is on Iboga Records' crew on the map
        artistService.saveLineUp(labelGig, List.of("Liquid Soul"), Instant.now());
        artistService.saveSummary(event(labelGig, "Digest Label Night " + unique, LocalDate.now().plusDays(9)));
        // A month away: not in this week's email
        artistService.saveLineUp(farAway, List.of(artist), Instant.now());
        artistService.saveSummary(event(farAway, "Digest Later " + unique, LocalDate.now().plusDays(30)));

        String username = uniqueUsername();
        register(username, PASSWORD);
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        String token = bearer(login(username, PASSWORD));
        mockMvc.perform(put("/api/me/follows/{slug}", "digest-act-" + unique).header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(put("/api/me/label-follows").param("label", "Iboga Records").header("Authorization", token)).andExpect(status().isOk());

        when(digestMailer.send(eq(username), org.mockito.ArgumentMatchers.anyString(), anyList())).thenReturn(true);
        weeklyDigest.send(LocalDate.now());
        verify(digestMailer).send(eq(username), eq(username + "@example.com"), argThat(gigs ->
                gigs.stream().anyMatch(g -> g.eventId() == followedGig && g.artists().contains(artist))
                        && gigs.stream().anyMatch(g -> g.eventId() == labelGig && g.artists().contains("Liquid Soul"))
                        && gigs.stream().noneMatch(g -> g.eventId() == farAway)));

        // Once a day: the next run the same day sends nothing more
        weeklyDigest.send(LocalDate.now());
        verify(digestMailer, times(1)).send(eq(username), org.mockito.ArgumentMatchers.anyString(), anyList());

        // Turned off: nothing next week
        mockMvc.perform(put("/api/me/weekly-digest").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(jsonPath("$.enabled").value(false));
        weeklyDigest.send(LocalDate.now().plusDays(7));
        verify(digestMailer, times(1)).send(eq(username), org.mockito.ArgumentMatchers.anyString(), anyList());
    }

    private static EventDto event(long id, String name, LocalDate day) {
        return new EventDto(id, name, "Town", "Serbia", "Club", "Scheduled", day, day, null, null, 1.0, 1.0);
    }
}
