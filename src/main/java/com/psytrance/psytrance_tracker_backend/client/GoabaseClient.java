package com.psytrance.psytrance_tracker_backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psytrance.psytrance_tracker_backend.exception.GoabaseUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The only class that talks to the Goabase API. It knows the URLs and the JSON
 * shape of their responses; the rest of the app works with {@link GoabaseParty}.
 */
@Component
public class GoabaseClient {

    // Goabase lists a few hundred parties; ask for more than that so none are cut off
    private static final int MAX_PARTIES = 1000;

    private final RestClient restClient;

    public GoabaseClient(@Value("${goabase.base-url}") String baseUrl) {
        // Goabase answers an unknown party id with a redirect, so redirects must not be
        // followed - otherwise we'd try to read their HTML page as JSON
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public List<GoabaseParty> fetchAllParties() {
        try {
            PartyListResponse response = restClient.get()
                    .uri(uri -> uri.path("/api/party/json/").queryParam("limit", MAX_PARTIES).build())
                    .retrieve()
                    .body(PartyListResponse.class);
            return response == null || response.partylist() == null ? List.of() : response.partylist();
        } catch (RestClientException e) {
            throw new GoabaseUnavailableException("Could not load events from Goabase", e);
        }
    }

    public Optional<GoabaseParty> fetchParty(long id) {
        try {
            return restClient.get()
                    .uri("/api/party/json/{id}", id)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is5xxServerError()) {
                            throw new GoabaseUnavailableException("Goabase returned " + response.getStatusCode(), null);
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return Optional.empty();
                        }
                        SinglePartyResponse body = response.bodyTo(SinglePartyResponse.class);
                        return Optional.ofNullable(body).map(SinglePartyResponse::party);
                    });
        } catch (RestClientException e) {
            throw new GoabaseUnavailableException("Could not load event " + id + " from Goabase", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PartyListResponse(List<GoabaseParty> partylist) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SinglePartyResponse(GoabaseParty party) {
    }
}
