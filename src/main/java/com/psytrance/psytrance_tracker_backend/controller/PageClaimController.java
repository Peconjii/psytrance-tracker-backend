package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.util.Links;
import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.PageClaims;
import com.psytrance.psytrance_tracker_backend.service.FormLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/** "Claim this page" (needs login and a confirmed email), the account's own claims, and the admins' decisions */
@RestController
public class PageClaimController {

    private final PageClaims pageClaims;
    private final Admins admins;
    private final FormLimits formLimits;
    private final int claimsPerDay;

    public PageClaimController(PageClaims pageClaims, Admins admins, FormLimits formLimits,
                               @Value("${app.limits.claims-per-day:10}") int claimsPerDay) {
        this.pageClaims = pageClaims;
        this.admins = admins;
        this.formLimits = formLimits;
        this.claimsPerDay = claimsPerDay;
    }

    /** kind: ARTIST, ORGANIZER, LABEL or FESTIVAL; proofUrl: a link that shows it is them (official site, Instagram) */
    public record ClaimRequest(@NotBlank String kind, @NotBlank @Size(max = 200) String target,
                               @Size(max = 500) @Pattern(regexp = Links.HTTPS_OR_BLANK, message = Links.HTTPS_MESSAGE) String proofUrl,
                               @Size(max = 1000) String message, String website) {
    }

    public record Decision(boolean approve, @Size(max = 500) String reason) {
    }

    /** status APPROVED when the email proved it at once, else PENDING (an admin decides) */
    @PostMapping("/api/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public PageClaims.Claim claim(@Valid @RequestBody ClaimRequest request, Authentication authentication) {
        // website: the hidden field only bots fill in
        if (FormLimits.isBot(request.website())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not send. Try again.");
        }
        formLimits.count("claim", authentication.getName(), claimsPerDay, Duration.ofDays(1),
                "You claimed many pages today. Try again tomorrow, or write to us through the feedback form.");
        return pageClaims.claim(request.kind(), request.target(), request.proofUrl(), request.message(), authentication.getName());
    }

    @GetMapping("/api/me/claims")
    public List<PageClaims.Claim> mine(Authentication authentication) {
        return pageClaims.mine(authentication.getName());
    }

    @GetMapping("/api/admin/claims")
    public List<PageClaims.Claim> pending(Authentication authentication) {
        admins.requireAdmin(authentication);
        return pageClaims.pending();
    }

    /** {@code POST /api/admin/claims/7 {"approve": true}}; approving links the owner */
    @PostMapping("/api/admin/claims/{id}")
    public PageClaims.Claim decide(@PathVariable long id, @Valid @RequestBody Decision decision, Authentication authentication) {
        return pageClaims.decide(id, decision.approve(), decision.reason(), admins.requireAdmin(authentication));
    }
}
