package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.NewPages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Not on the map yet?" (needs login and a confirmed email), the account's own requests, and the admins' decisions */
@RestController
public class NewPageController {

    private final NewPages newPages;
    private final Admins admins;

    public NewPageController(NewPages newPages, Admins admins) {
        this.newPages = newPages;
        this.admins = admins;
    }

    /** kind: ARTIST, ORGANIZER, LABEL or FESTIVAL */
    public record PageRequest(@NotBlank String kind, @NotBlank @Size(max = 200) String name, @Size(max = 100) String country,
                              @Size(max = 500) @Pattern(regexp = "\\s*|https://\\S+", message = "must be an https:// address") String websiteUrl,
                              @Size(max = 500) @Pattern(regexp = "\\s*|https://\\S+", message = "must be an https:// address") String proofUrl,
                              @Size(max = 300) String genres, @Size(max = 1000) String message) {
    }

    public record Decision(boolean approve, @Size(max = 500) String reason) {
    }

    @PostMapping("/api/page-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public NewPages.Request ask(@Valid @RequestBody PageRequest request, Authentication authentication) {
        return newPages.ask(new NewPages.Ask(request.kind(), request.name(), request.country(), request.websiteUrl(),
                request.proofUrl(), request.genres(), request.message()), authentication.getName());
    }

    @GetMapping("/api/me/page-requests")
    public List<NewPages.Request> mine(Authentication authentication) {
        return newPages.mine(authentication.getName());
    }

    @GetMapping("/api/admin/page-requests")
    public List<NewPages.Request> pending(Authentication authentication) {
        admins.requireAdmin(authentication);
        return newPages.pending();
    }

    /** {@code POST /api/admin/page-requests/7 {"approve": true}}; approving an artist or organizer makes the page */
    @PostMapping("/api/admin/page-requests/{id}")
    public NewPages.Request decide(@PathVariable long id, @Valid @RequestBody Decision decision, Authentication authentication) {
        return newPages.decide(id, decision.approve(), decision.reason(), admins.requireAdmin(authentication));
    }
}
