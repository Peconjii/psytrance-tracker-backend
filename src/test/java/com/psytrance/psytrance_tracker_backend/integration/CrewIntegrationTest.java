package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.service.events.CrewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrewIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(720_000);

    private final List<GoabaseParty> goabaseParties = new ArrayList<>();

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CrewService crewService;

    private record Person(String name, String token) {
    }

    @Test
    void theLinkNeedsAnAccountAndJoiningMarksYouGoing() throws Exception {
        long eventId = event("Ozora");
        Person captain = person();
        String code = createCrew(captain, eventId, "Ekipa iz NS");

        mockMvc.perform(get("/api/crews/{code}/preview", code)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/crews/{code}/join", code)).andExpect(status().isUnauthorized());

        Person friend = person();
        mockMvc.perform(get("/api/crews/{code}", code).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/crews/{code}/preview", code).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ekipa iz NS"))
                .andExpect(jsonPath("$.members").value(1))
                .andExpect(jsonPath("$.member").value(false));

        mockMvc.perform(post("/api/crews/{code}/join", code).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].username").value(captain.name()))
                .andExpect(jsonPath("$.members[0].captain").value(true))
                .andExpect(jsonPath("$.captain").value(false));

        mockMvc.perform(get("/api/events/{id}/going", eventId).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(jsonPath("$.going").value(true))
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/api/crews/mine").header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(jsonPath("$[0].code").value(code));
    }

    @Test
    void aNewLinkStopsTheOldOneAndOnlyTheCaptainRemovesPeople() throws Exception {
        long eventId = event("Boom");
        Person captain = person();
        Person friend = person();
        String code = createCrew(captain, eventId, "Crew");
        join(friend, code);

        mockMvc.perform(post("/api/crews/{code}/link", code).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/crews/{code}/members/{u}", code, captain.name()).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(post("/api/crews/{code}/link", code).header(HttpHeaders.AUTHORIZATION, bearer(captain.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.code");
        Person late = person();
        mockMvc.perform(post("/api/crews/{code}/join", code).header(HttpHeaders.AUTHORIZATION, bearer(late.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/crews/{code}/members/{u}", fresh, friend.name()).header(HttpHeaders.AUTHORIZATION, bearer(captain.token())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/crews/{code}", fresh).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void someoneTheCaptainRemovedCantComeBackWithTheSameLink() throws Exception {
        long eventId = event("Ozora");
        Person captain = person();
        Person friend = person();
        String code = createCrew(captain, eventId, "Crew");
        join(friend, code);
        mockMvc.perform(delete("/api/crews/{code}/members/{u}", code, friend.name()).header(HttpHeaders.AUTHORIZATION, bearer(captain.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/crews/{code}/join", code).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(status().isForbidden());
        // Removing someone who isn't in the crew is a 404, not a silent success
        mockMvc.perform(delete("/api/crews/{code}/members/{u}", code, "nobody-here").header(HttpHeaders.AUTHORIZATION, bearer(captain.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyWhoAddedOrBringsAThingOrTheCaptainDeletesIt() throws Exception {
        long eventId = event("Ozora");
        Person captain = person();
        Person a = person();
        Person b = person();
        String code = createCrew(captain, eventId, "Crew");
        join(a, code);
        join(b, code);
        String body = mockMvc.perform(post("/api/crews/{code}/items", code).header(HttpHeaders.AUTHORIZATION, bearer(a.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tent\"}"))
                .andReturn().getResponse().getContentAsString();
        long tent = ((Number) JsonPath.read(body, "$.items[0].id")).longValue();

        mockMvc.perform(delete("/api/crews/{code}/items/{id}", code, tent).header(HttpHeaders.AUTHORIZATION, bearer(b.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/crews/{code}/items/{id}", code, tent).header(HttpHeaders.AUTHORIZATION, bearer(captain.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void packingListSaysWhoBringsWhat() throws Exception {
        long eventId = event("Forest gathering");
        Person captain = person();
        Person friend = person();
        String code = createCrew(captain, eventId, "Crew");
        join(friend, code);

        String body = mockMvc.perform(post("/api/crews/{code}/items", code).header(HttpHeaders.AUTHORIZATION, bearer(captain.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gazebo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].bringer").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long gazebo = ((Number) JsonPath.read(body, "$.items[0].id")).longValue();
        // The same thing twice stays one line
        mockMvc.perform(post("/api/crews/{code}/items", code).header(HttpHeaders.AUTHORIZATION, bearer(friend.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"gazebo\"}"))
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(post("/api/crews/{code}/items/{id}/bring", code, gazebo).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(jsonPath("$.items[0].bringer").value(friend.name()))
                .andExpect(jsonPath("$.items[0].mine").value(true));
        mockMvc.perform(post("/api/crews/{code}/items/{id}/bring", code, gazebo).header(HttpHeaders.AUTHORIZATION, bearer(captain.token())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/crews/{code}/items/{id}/bring", code, gazebo).header(HttpHeaders.AUTHORIZATION, bearer(friend.token())))
                .andExpect(jsonPath("$.items[0].bringer").doesNotExist());
    }

    @Test
    void mustSeeSetsComeFromFollowsAndPicksAndShowClashes() throws Exception {
        long eventId = event("Ozora");
        LocalDateTime night = LocalDate.now().plusDays(10).atTime(23, 0);
        slot(eventId, 0, "Main", "Astrix (live)", night, night.plusHours(2));
        slot(eventId, 1, "Dome", "Ott", night.plusHours(1), null);
        slot(eventId, 2, "Main", "Unknown DJ", night.plusHours(5), null);
        Person captain = person();
        Person friend = person();
        String code = createCrew(captain, eventId, "Crew");
        join(friend, code);
        follow(friend, "astrix", "Astrix");

        mockMvc.perform(post("/api/crews/{code}/picks", code).header(HttpHeaders.AUTHORIZATION, bearer(captain.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artist\":\"Ott\",\"startsAt\":\"" + night.plusHours(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets", hasSize(2)))
                .andExpect(jsonPath("$.sets[0].artist").value("Astrix (live)"))
                .andExpect(jsonPath("$.sets[0].followedBy", contains(friend.name())))
                .andExpect(jsonPath("$.sets[0].clashesWith", contains("Ott")))
                .andExpect(jsonPath("$.sets[1].addedBy").value(captain.name()))
                .andExpect(jsonPath("$.sets[1].clashesWith", containsInAnyOrder("Astrix (live)")));

        mockMvc.perform(post("/api/crews/{code}/picks", code).header(HttpHeaders.AUTHORIZATION, bearer(captain.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artist\":\"Nobody\",\"startsAt\":\"" + night + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crewsGoAWeekAfterTheEvent() throws Exception {
        long eventId = event("Old party");
        Person captain = person();
        String code = createCrew(captain, eventId, "Crew");
        jdbc.sql("update crew set ends_on = :d where code = :c").param("d", LocalDate.now().minusDays(8)).param("c", code).update();

        crewService.deleteOldCrews();

        mockMvc.perform(get("/api/crews/{code}", code).header(HttpHeaders.AUTHORIZATION, bearer(captain.token())))
                .andExpect(status().isNotFound());
    }

    private String createCrew(Person p, long eventId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/crews/event/{id}", eventId).header(HttpHeaders.AUTHORIZATION, bearer(p.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captain").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private void join(Person p, String code) throws Exception {
        mockMvc.perform(post("/api/crews/{code}/join", code).header(HttpHeaders.AUTHORIZATION, bearer(p.token())))
                .andExpect(status().isOk());
    }

    private void follow(Person p, String slug, String name) {
        jdbc.sql("""
                        insert into artist_follows (user_id, artist_slug, artist_name, created_at)
                        select id, :s, :n, now() from app_user where username = :u""")
                .param("s", slug).param("n", name).param("u", p.name()).update();
    }

    private void slot(long eventId, int position, String stage, String artist, LocalDateTime start, LocalDateTime end) {
        jdbc.sql("""
                        insert into event_timetable (event_id, position, stage, artist_name, starts_at, ends_at, source)
                        values (:e, :p, :st, :a, :s, :end, 'MANUAL')""")
                .param("e", eventId).param("p", position).param("st", stage).param("a", artist)
                .param("s", Timestamp.valueOf(start)).param("end", end == null ? null : Timestamp.valueOf(end)).update();
    }

    private long event(String name) {
        long id = NEXT_EVENT_ID.getAndIncrement();
        goabaseParties.add(new GoabaseParty(id, name, LocalDate.now().plusDays(10) + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Hungary", "Ozora", 46.75, 18.4, null, null));
        when(goabaseClient.fetchAllParties()).thenReturn(List.copyOf(goabaseParties));
        return id;
    }

    private Person person() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");
        return new Person(username, login(username, "password123"));
    }
}
