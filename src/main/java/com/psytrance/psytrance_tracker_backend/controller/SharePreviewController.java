package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.SharePreview;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Link previews for the bots of chat apps and social networks (vercel.json sends only them here) */
@RestController
public class SharePreviewController {

    private final SharePreview sharePreview;

    public SharePreviewController(SharePreview sharePreview) {
        this.sharePreview = sharePreview;
    }

    /** Example: {@code GET /api/share/artists/ajja}, {@code /api/share/events/118537}, {@code /api/share/labels/TIP Records} */
    @GetMapping("/api/share/{kind}/{key}")
    public ResponseEntity<String> preview(@PathVariable String kind, @PathVariable String key) {
        return ResponseEntity.ok()
                // UTF-8 said out loud: names like "Zürich" or "Hallucinogen · live" must not turn into garbage
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(sharePreview.html(kind, key));
    }
}
