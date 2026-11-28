package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.account.PushNotifications;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Android app tells which phone (its Firebase token) belongs to the logged-in account. Needs login. */
@RestController
public class PushController {

    public record Device(@NotBlank @Size(max = 4096) String token) {
    }

    private final PushNotifications pushNotifications;

    public PushController(PushNotifications pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    @PostMapping("/api/me/push")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody Device body, Authentication auth) {
        pushNotifications.register(auth.getName(), body.token().strip());
    }

    @DeleteMapping("/api/me/push")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@Valid @RequestBody Device body, Authentication auth) {
        pushNotifications.unregister(auth.getName(), body.token().strip());
    }
}
