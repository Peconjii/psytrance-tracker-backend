package com.psytrance.psytrance_tracker_backend.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** An upload is judged by its first bytes, never by its name or the type the browser claims */
class ImagesTest {

    private static byte[] bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }

    @Test
    void knowsJpegPngAndWebp() {
        assertThat(Images.typeOf(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("image/jpeg");
        assertThat(Images.typeOf(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).isEqualTo("image/png");
        assertThat(Images.typeOf("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo("image/webp");
    }

    @Test
    void refusesEverythingElse() {
        assertThat(Images.typeOf("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(Images.typeOf("GIF89a".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(Images.typeOf("<html><script>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(Images.typeOf("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(Images.typeOf(bytes(0xFF, 0xD8))).isNull();
        assertThat(Images.typeOf(new byte[0])).isNull();
    }
}
