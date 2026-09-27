package com.psytrance.psytrance_tracker_backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psytrance.psytrance_tracker_backend.exception.YouTubeUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The only class that talks to the YouTube Data API. Without an API key it is switched off
 * and the app simply shows no videos. Every search costs 100 of the 10,000 free daily quota
 * units, so callers are expected to cache what they find.
 */
@Component
public class YouTubeClient {

    private static final int MAX_RESULTS = 10;

    private final RestClient restClient;
    private final String apiKey;

    public YouTubeClient(@Value("${youtube.base-url}") String baseUrl,
                         @Value("${youtube.api-key:}") String apiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Videos that can be embedded on other sites, best match first. */
    public List<YouTubeVideo> searchVideos(String query) {
        if (!isEnabled()) {
            return List.of();
        }
        try {
            SearchResponse response = restClient.get()
                    .uri(uri -> uri.path("/youtube/v3/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("videoEmbeddable", "true")
                            .queryParam("maxResults", MAX_RESULTS)
                            .queryParam("q", query)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);
            if (response == null || response.items() == null) {
                return List.of();
            }
            return response.items().stream()
                    .filter(item -> item.id() != null && item.id().videoId() != null && item.snippet() != null)
                    // YouTube sends titles HTML-escaped: "Ozora 2025 &#39;Official&#39; Aftermovie"
                    .map(item -> new YouTubeVideo(item.id().videoId(), HtmlUtils.htmlUnescape(Objects.toString(item.snippet().title(), ""))))
                    .toList();
        } catch (RestClientException e) {
            // Also a used-up daily quota, which YouTube answers with 403
            throw new YouTubeUnavailableException("YouTube search failed", e);
        }
    }

    public record YouTubeVideo(String videoId, String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(ItemId id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemId(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Snippet(String title) {
    }
}
