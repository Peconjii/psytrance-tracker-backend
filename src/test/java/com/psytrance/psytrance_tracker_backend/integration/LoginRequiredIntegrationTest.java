package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Everything that writes, and every page of one's own, found in the application itself like in
 * {@link AdminAccessIntegrationTest}: a guest gets 401 before any of it runs, so an endpoint added later can't be
 * left open by mistake. Only the forms a guest needs are open.
 */
class LoginRequiredIntegrationTest extends IntegrationTest {

    // A guest logs in, makes an account, gets a new password, confirms the email and sends feedback
    private static final Set<String> OPEN_TO_GUESTS = Set.of("/api/auth/login", "/api/auth/register",
            "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/verify-email", "/api/feedback");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private List<Map.Entry<HttpMethod, String>> endpoints() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream()
                        .filter(path -> path.startsWith("/api/") && !path.startsWith("/api/admin/"))
                        .flatMap(path -> info.getMethodsCondition().getMethods().stream()
                                .map(method -> Map.entry(HttpMethod.valueOf(method.name()), path))))
                .toList();
    }

    @Test
    void writingNeedsALogin() throws Exception {
        List<Map.Entry<HttpMethod, String>> writes = endpoints().stream()
                .filter(endpoint -> endpoint.getKey() != HttpMethod.GET && !OPEN_TO_GUESTS.contains(endpoint.getValue()))
                .toList();
        // Profiles, pages, follows, going, reviews, uploads...: if this drops, the scan stopped finding them
        assertThat(writes).hasSizeGreaterThanOrEqualTo(30);

        for (Map.Entry<HttpMethod, String> endpoint : writes) {
            String path = endpoint.getValue().replaceAll("\\{[^}]+}", "1");
            int status = mockMvc.perform(request(endpoint.getKey(), path).param("label", "TIP Records").param("id", "ozora")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as("guest on %s %s", endpoint.getKey(), path).isEqualTo(401);
        }
    }

    @Test
    void ownPagesAndEditorsNeedALogin() throws Exception {
        // Read-only, but someone's own: the account, its lists, and what page owners see while editing
        List<String> own = endpoints().stream()
                .filter(endpoint -> endpoint.getKey() == HttpMethod.GET)
                .map(Map.Entry::getValue)
                .filter(path -> path.startsWith("/api/me") || path.endsWith("/edit") || path.endsWith("owner-stats"))
                .distinct()
                .toList();
        assertThat(own).hasSizeGreaterThanOrEqualTo(10);

        for (String endpoint : own) {
            String path = endpoint.replaceAll("\\{[^}]+}", "1");
            int status = mockMvc.perform(get(path).param("label", "TIP Records").param("id", "ozora"))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as("guest on GET %s", path).isEqualTo(401);
        }
    }

    @Test
    void forgotPasswordDoesNotTellWhoHasAnAccount() throws Exception {
        String username = uniqueUsername();
        register(username, "secret-password-123");

        String known = mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s@example.com\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody-%s@example.com\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown);

        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"made-up-token\", \"newPassword\": \"another-password-456\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void theEventMapIsPublic() throws Exception {
        mockMvc.perform(get("/api/events/map")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }
}
