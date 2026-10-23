package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.AccountDeletion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Delete my account", from the profile */
@RestController
public class AccountController {

    private final AccountDeletion accountDeletion;

    public AccountController(AccountDeletion accountDeletion) {
        this.accountDeletion = accountDeletion;
    }

    public record Confirmation(@NotBlank String password) {
    }

    /**
     * {@code POST /api/me/delete-account {"password": "…"}}: the account and what is the person's are gone for good.
     * A POST with the password in the body (a DELETE with a body gets lost on some proxies).
     */
    @PostMapping("/api/me/delete-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody Confirmation confirmation, Authentication authentication) {
        accountDeletion.delete(authentication.getName(), confirmation.password());
    }
}
