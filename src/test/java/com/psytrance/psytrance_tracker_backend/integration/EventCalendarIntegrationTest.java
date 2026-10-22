package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Add to calendar": the event as a .ics file with its times in UTC */
class EventCalendarIntegrationTest extends IntegrationTest {

    @Test
    void anEventIsACalendarFileWithUtcTimes() throws Exception {
        long id = 996_001;
        when(goabaseClient.fetchParty(id)).thenReturn(Optional.of(new GoabasePartyDetails(id, "Forest; Night, Vol. 2",
                "2026-11-14T22:00:00+01:00", "2026-11-15T10:00:00+01:00", "Open Air", "Scheduled", "Germany", "Berlin",
                52.5, 13.4, null, null, null, "", "", "", "", "", null)));

        mockMvc.perform(get("/api/events/{id}/calendar.ics", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andExpect(header().string("Content-Disposition", containsString("event-996001.ics")))
                .andExpect(content().string(containsString("DTSTART:20261114T210000Z\r\n")))
                .andExpect(content().string(containsString("DTEND:20261115T090000Z\r\n")))
                .andExpect(content().string(containsString("SUMMARY:Forest\\; Night\\, Vol. 2\r\n")))
                .andExpect(content().string(containsString("LOCATION:Berlin\\, Germany\r\n")));
    }

    @Test
    void anUnknownEventIsNotFound() throws Exception {
        when(goabaseClient.fetchParty(996_002)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/events/{id}/calendar.ics", 996_002))
                .andExpect(status().isNotFound());
    }
}
