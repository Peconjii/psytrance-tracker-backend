package com.psytrance.psytrance_tracker_backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The labels of Map of Psy ({@code map/labels.json}, copied from the frontend) and the map artists on each. */
@Component
public class MapLabels {

    private static final String RESOURCE = "map/labels.json";

    private final Map<String, List<String>> artistsByLabel;

    @Autowired
    public MapLabels(JsonMapper jsonMapper) {
        this(read(jsonMapper));
    }

    // For tests: a small hand-made map
    MapLabels(Map<String, List<String>> artistsByLabel) {
        this.artistsByLabel = Map.copyOf(artistsByLabel);
    }

    /** The artists on a label, by their names on the map; empty when there is no such label */
    public Optional<List<String>> artists(String label) {
        return Optional.ofNullable(label == null ? null : artistsByLabel.get(label));
    }

    private static Map<String, List<String>> read(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, Label> labels = jsonMapper.readValue(in,
                    jsonMapper.getTypeFactory().constructMapType(TreeMap.class, String.class, Label.class));
            Map<String, List<String>> artists = new TreeMap<>();
            labels.forEach((name, label) -> artists.put(name, List.copyOf(label.artists())));
            return artists;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Label(List<String> artists) {
    }
}
