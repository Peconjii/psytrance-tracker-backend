package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.service.ReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reminder job against a real database. No mail server is configured in tests,
 * so the "email" is written to the log, which counts as sent.
 */
class ReminderIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(800_000);

    @Autowired
    private ReminderService reminderService;

    @Test
    void savedAndGoingEventGetsOneReminderOnlyOnce() throws Exception {
        long eventId = eventStartingTomorrow();
        String username = uniqueUsername();
        long userId = register(username, "password123");
        String token = login(username, "password123");

        // Saved and going: still only one email
        mockMvc.perform(post("/api/favorites/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": "%d", "eventName": "Tomorrow Party"}
                                """.formatted(eventId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/events/{id}/going", eventId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        assertThat(reminderService.sendDueReminders()).isEqualTo(1);
        // The next hourly run finds it was already sent
        assertThat(reminderService.sendDueReminders()).isZero();
    }

    @Test
    void usersCanTurnRemindersOff() throws Exception {
        long eventId = eventStartingTomorrow();
        String username = uniqueUsername();
        register(username, "password123");
        String token = login(username, "password123");
        mockMvc.perform(put("/api/events/{id}/going", eventId).header(HttpHeaders.AUTHORIZATION, bearer(token)));

        mockMvc.perform(put("/api/me/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.eventReminders").value(false));
        assertThat(reminderService.sendDueReminders()).isZero();
    }

    @Test
    void reminderSettingNeedsLoginAndAValue() throws Exception {
        mockMvc.perform(put("/api/me/reminders").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isUnauthorized());

        String username = uniqueUsername();
        register(username, "password123");
        mockMvc.perform(put("/api/me/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(username, "password123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** The fake Goabase list has only this event, so only this test's users are reminded. */
    private long eventStartingTomorrow() {
        long id = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(id, "Tomorrow Party",
                LocalDate.now().plusDays(1) + "T22:00:00+02:00", null, "Party", "Scheduled",
                "Serbia", "Novi Sad", 45.25, 19.84, null, null)));
        return id;
    }
}
