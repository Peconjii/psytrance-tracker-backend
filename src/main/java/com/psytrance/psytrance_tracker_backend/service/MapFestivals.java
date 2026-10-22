package com.psytrance.psytrance_tracker_backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    // Festivals admins added to the map (table map_festivals, see MapExtras): id -> name
    private volatile Map<String, String> added = Map.of();

    public Optional<String> name(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(nameById.getOrDefault(id, added.get(id)));
    }

    /** The id of the festival with this name, ignoring case, spaces and punctuation */
    public Optional<String> idOf(String name) {
        String wanted = ArtistNames.slug(name);
        return Stream.concat(nameById.entrySet().stream(), added.entrySet().stream())
                .filter(e -> ArtistNames.slug(e.getValue()).equals(wanted))
                .map(Map.Entry::getKey).findFirst();
    }

    /** Every festival of the map, the added ones too: id -> name */
    public Map<String, String> all() {
        Map<String, String> all = new HashMap<>(nameById);
        all.putAll(added);
        return all;
    }

    /** Uses these festivals admins added to the map ({@code id -> name}) instead of the ones used so far */
    public void useAddedFestivals(Map<String, String> festivals) {
        this.added = Map.copyOf(festivals);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Festival(String id, String name) {
    }
}
