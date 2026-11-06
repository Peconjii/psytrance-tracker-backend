package com.psytrance.psytrance_tracker_backend.service.map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The festival stations from map/festivals.json */
class MapFestivalsTest {

    private final MapFestivals festivals = new MapFestivals(JsonMapper.builder().build());

    @Test
    void aRenamedFestivalIsFoundByItsNewAndItsFormerName() {
        assertThat(festivals.name("jungle")).contains("Jungle Festival");
        assertThat(festivals.idOf("Jungle Festival")).contains("jungle");
        // Artist pages written before the rename still point to it
        assertThat(festivals.idOf("Jungle Island")).contains("jungle");
        assertThat(festivals.idOf("jungle-island")).contains("jungle");
    }

    @Test
    void festivalsWithoutAliasesWorkAsBefore() {
        assertThat(festivals.idOf("Pyramid Festival")).contains("pyramid");
        assertThat(festivals.idOf("Nonexistent Fest")).isEmpty();
    }
}
