package com.psytrance.psytrance_tracker_backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The festival stations of Map of Psy ({@code map/festivals.json}, copied from the frontend): id -> name */
@Component
public class MapFestivals {

    private static final String RESOURCE = "map/festivals.json";

    private final Map<String, String> nameById;

    public MapFestivals(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            List<Festival> festivals = jsonMapper.readValue(in,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, Festival.class));
            this.nameById = festivals.stream().collect(Collectors.toUnmodifiableMap(Festival::id, Festival::name));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    public Optional<String> name(String id) {
        return Optional.ofNullable(id == null ? null : nameById.get(id));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Festival(String id, String name) {
    }
}
