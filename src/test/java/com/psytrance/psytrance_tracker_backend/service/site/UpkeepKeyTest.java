package com.psytrance.psytrance_tracker_backend.service.site;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The name key that groups probable duplicate artists in Admin -> Upkeep */
class UpkeepKeyTest {

    @Test
    void bracketsDjLiveAndPunctuationDontCount() {
        assertThat(Upkeep.key("Astrix")).isEqualTo("astrix");
        assertThat(Upkeep.key("Astrix (IL)")).isEqualTo("astrix");
        assertThat(Upkeep.key("DJ Astrix")).isEqualTo("astrix");
        assertThat(Upkeep.key("Astrix Live")).isEqualTo("astrix");
        assertThat(Upkeep.key("Ace Ventura [ISR]")).isEqualTo(Upkeep.key("Ace-Ventura"));
        assertThat(Upkeep.key("The Grateful Dead")).isEqualTo(Upkeep.key("Grateful Dead"));
        assertThat(Upkeep.key("Kölsch")).isEqualTo("kolsch");
    }

    @Test
    void differentArtistsStayApart() {
        assertThat(Upkeep.key("Astral Projection")).isNotEqualTo(Upkeep.key("Astrix"));
        assertThat(Upkeep.key("Liveset")).isEqualTo("liveset");
    }
}
