package com.psytrance.psytrance_tracker_backend.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistNamesTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Ace Ventura    | ace-ventura",
            "ACE VENTURA    | ace-ventura",
            "Ace-Ventura    | ace-ventura",
            "Ëlement        | element",
            "SUN-WU-KONG    | sun-wu-kong",
            "Astrix & Ritmo | astrix-ritmo",
            "Космос         | космос",
            "'!!!'          | ''",
    })
    void spellingsOfTheSameArtistShareASlug(String name, String slug) {
        assertThat(ArtistNames.slug(name)).isEqualTo(slug);
    }

    @Test
    void normalCaseReplacesAllCapsButNotTheOtherWayRound() {
        assertThat(ArtistNames.preferredName("ACE VENTURA", "Ace Ventura")).isEqualTo("Ace Ventura");
        assertThat(ArtistNames.preferredName("Ace Ventura", "ACE VENTURA")).isEqualTo("Ace Ventura");
        // The first spelling stays when neither is in capitals
        assertThat(ArtistNames.preferredName("Ace Ventura", "ace ventura")).isEqualTo("Ace Ventura");
    }
}
