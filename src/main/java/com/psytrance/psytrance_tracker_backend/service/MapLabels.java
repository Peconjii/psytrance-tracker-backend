package com.psytrance.psytrance_tracker_backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The labels of Map of Psy ({@code map/labels.json}, copied from the frontend) and the artists on each: the map's crew,
 * with the changes admins approved on top (see LabelPages), so following and the numbers use the crew as it is now.
 */
@Component
public class MapLabels {

    private static final String RESOURCE = "map/labels.json";

    private final Map<String, List<String>> artistsByLabel;
    // label -> artist -> on the label (true) or taken off (false); replaced as a whole, volatile for other threads
    private volatile Map<String, Map<String, Boolean>> crewChanges = Map.of();

    @Autowired
    public MapLabels(JsonMapper jsonMapper) {
        this(read(jsonMapper));
    }

    // For tests: a small hand-made map
    MapLabels(Map<String, List<String>> artistsByLabel) {
        this.artistsByLabel = Map.copyOf(artistsByLabel);
    }

    public boolean isLabel(String label) {
        return label != null && artistsByLabel.containsKey(label);
    }

    public Set<String> names() {
        return artistsByLabel.keySet();
    }

    /** The artists on a label now: the map's, plus the ones added, minus the ones taken off; empty when no such label */
    public Optional<List<String>> artists(String label) {
        if (!isLabel(label)) {
            return Optional.empty();
        }
        Map<String, Boolean> changes = crewChanges.getOrDefault(label, Map.of());
        List<String> crew = new ArrayList<>(artistsByLabel.get(label).stream()
                .filter(artist -> !Boolean.FALSE.equals(changes.get(artist)))
                .toList());
        changes.forEach((artist, added) -> {
            if (added && !crew.contains(artist)) {
                crew.add(artist);
            }
        });
        crew.sort(String.CASE_INSENSITIVE_ORDER);
        return Optional.of(List.copyOf(crew));
    }

    /** Uses these approved crew changes ({@code label -> artist -> added}) instead of the ones used so far */
    public void useCrewChanges(Map<String, Map<String, Boolean>> changes) {
        Map<String, Map<String, Boolean>> copy = new TreeMap<>();
        changes.forEach((label, artists) -> copy.put(label, Map.copyOf(artists)));
        this.crewChanges = copy;
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
