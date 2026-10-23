package com.psytrance.psytrance_tracker_backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared rules of form fields: links, picture fields and blank text */
class FieldRulesTest {

    @Test
    void linkFieldsTakeHttpsOnly() {
        assertThat("https://instagram.com/ajja").matches(Links.HTTPS).matches(Links.HTTPS_OR_BLANK);
        assertThat("").matches(Links.HTTPS_OR_BLANK).doesNotMatch(Links.HTTPS);
        assertThat("   ").matches(Links.HTTPS_OR_BLANK);
        for (String refused : new String[]{"http://plain.example", "javascript:alert(1)", "https://has space.example", "ftp://x"}) {
            assertThat(refused).doesNotMatch(Links.HTTPS_OR_BLANK);
        }
    }

    @Test
    void pictureFieldsTakeHttpsOrAPictureUploadedHere() {
        assertThat("https://pics.example/logo.png").matches(ImageUrls.PATTERN);
        assertThat("http://localhost:8080/api/images/12").matches(ImageUrls.PATTERN);
        assertThat("").matches(ImageUrls.PATTERN);
        assertThat("http://evil.example/api/images/12").doesNotMatch(ImageUrls.PATTERN);
        assertThat("http://localhost:8080/api/images/12/../../admin").doesNotMatch(ImageUrls.PATTERN);
    }

    @Test
    void blankTextIsNoValue() {
        assertThat(Texts.blankToNull(null)).isNull();
        assertThat(Texts.blankToNull("  \n ")).isNull();
        assertThat(Texts.blankToNull("  Forest crew ")).isEqualTo("Forest crew");
    }
}
