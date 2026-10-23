package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.Images;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** Uploading a picture (logged in) and showing it (anyone) */
@RestController
public class ImageController {

    private final Images images;

    public ImageController(Images images) {
        this.images = images;
    }

    /** {@code POST /api/images} with the picture as {@code file}: {"url": "…/api/images/12"} to put in a picture field */
    @PostMapping("/api/images")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        if (file.getSize() > Images.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The picture is too big (2 MB at most)");
        }
        return Map.of("url", images.upload(file.getBytes(), authentication.getName()));
    }

    /** A picture never changes (a new one gets a new address), so browsers keep it for a year */
    @GetMapping("/api/images/{id}")
    public ResponseEntity<byte[]> show(@PathVariable long id) {
        Images.Image image = images.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No picture " + id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.data());
    }
}
