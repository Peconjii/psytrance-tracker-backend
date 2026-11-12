package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.service.map.MapExtras;
import com.psytrance.psytrance_tracker_backend.service.map.MapGenres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A festival or label added from the panel never lands on another festival, label or star of the map */
class MarkerSpacingIntegrationTest extends IntegrationTest {

    @Autowired
    private MapExtras mapExtras;
    @Autowired
    private MapGenres mapGenres;
    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void newMarkersKeepTheirDistance() throws Exception {
        List<int[]> taken = new ArrayList<>();
        try (var festivals = new ClassPathResource("map/festivals.json").getInputStream();
             var labels = new ClassPathResource("map/labels.json").getInputStream()) {
            for (JsonNode f : jsonMapper.readTree(festivals)) taken.add(new int[]{f.get("x").asInt(), f.get("y").asInt()});
            for (JsonNode l : jsonMapper.readTree(labels)) taken.add(new int[]{l.get("x").asInt(), l.get("y").asInt()});
        }
        mapGenres.all().stream().filter(g -> g.x() != null).forEach(g -> taken.add(new int[]{g.x(), g.y()}));

        // The crowded dark corner (Pachamama, Parvati Records, Magna Mysteria...) and a few others
        for (List<String> genres : List.of(List.of("dark"), List.of("dark", "psycore"), List.of("hitech"), List.of("forest", "dark"), List.of("fullon"))) {
            for (int before = 0; before < 6; before++) {
                int[] at = mapExtras.freeSpot(genres, before, 90, 0);
                double nearest = taken.stream().mapToDouble(t -> Math.hypot(t[0] - at[0], t[1] - at[1])).min().orElseThrow();
                assertThat(nearest).as("%s, marker %d at %d,%d", genres, before, at[0], at[1]).isGreaterThanOrEqualTo(80);
            }
        }
    }
}
