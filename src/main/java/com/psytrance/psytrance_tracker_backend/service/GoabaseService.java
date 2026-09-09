package com.psytrance.psytrance_tracker_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GoabaseService {

    private final WebClient webClient;

    public GoabaseService() {
        // Increase in-memory buffer limit to 10MB to handle large API responses
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl("https://www.goabase.net")
                .exchangeStrategies(exchangeStrategies)
                .build();
    }

    public Object getEvents(int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/party/json/")
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }

    public Object getEventsByCountry(String country, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/party/json/")
                        .queryParam("country", country)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }

    public Object getEventById(int id) {
        return webClient.get()
                .uri("/api/party/json/" + id)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }
}