package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Every admin endpoint, found in the application itself rather than listed by hand, so one added later is
 * checked too: guests get 401 and logged-in users who aren't admins get 403, before any admin code runs.
 */
class AdminAccessIntegrationTest extends IntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyAdminEndpointIsClosedToGuestsAndUsers() throws Exception {
        List<Map.Entry<HttpMethod, String>> endpoints = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream()
                        .filter(path -> path.startsWith("/api/admin/"))
                        .flatMap(path -> info.getMethodsCondition().getMethods().stream()
                                .map(method -> Map.entry(HttpMethod.valueOf(method.name()), path))))
                .toList();
        // Feedback, artists, events, timetables and history: if this drops, the scan stopped finding them
        assertThat(endpoints).hasSizeGreaterThanOrEqualTo(12);

        String username = uniqueUsername();
        register(username, "secret-password-123");
        String userToken = login(username, "secret-password-123");

        for (Map.Entry<HttpMethod, String> endpoint : endpoints) {
            // Path variables like {id} get a real-looking value
            String path = endpoint.getValue().replaceAll("\\{[^}]+}", "1");
            int guest = mockMvc.perform(request(endpoint.getKey(), path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            int user = mockMvc.perform(request(endpoint.getKey(), path).contentType(MediaType.APPLICATION_JSON).content("{}")
                            .header("Authorization", bearer(userToken)))
                    .andReturn().getResponse().getStatus();
            assertThat(guest).as("guest on %s %s", endpoint.getKey(), path).isEqualTo(401);
            assertThat(user).as("user on %s %s", endpoint.getKey(), path).isEqualTo(403);
        }
    }
}
