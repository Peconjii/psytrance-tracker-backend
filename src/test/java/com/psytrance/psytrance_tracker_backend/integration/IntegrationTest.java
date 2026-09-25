package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for tests that run the whole application: real HTTP handling through MockMvc, real security,
 * and a real PostgreSQL started in Docker by Testcontainers. Only Goabase is faked, so tests
 * don't depend on the internet.
 */
@SpringBootTest(properties = {
        "jwt.secret=integration-test-jwt-secret-that-is-long-enough",
        "goabase.cache-ttl=0s" // no caching between tests, so each test's fake Goabase data is used
})
@AutoConfigureMockMvc
abstract class IntegrationTest {

    // One database for all test classes. Started manually instead of with @Container, because
    // Spring reuses the application context between test classes and it must keep the same database.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected GoabaseClient goabaseClient;

    /** Usernames are unique per test, because all tests share one database. */
    protected static String uniqueUsername() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Registers a user and returns their database id. */
    protected long register(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                                """.formatted(username, username, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Logs in and returns the JWT. */
    protected String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
