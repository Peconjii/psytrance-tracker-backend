package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.PageClaims;
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

/** "Claim this page" (needs login and a confirmed email), the account's own claims, and the admins' decisions */
@RestController
public class PageClaimController {

    private final PageClaims pageClaims;
    private final Admins admins;

    public PageClaimController(PageClaims pageClaims, Admins admins) {
        this.pageClaims = pageClaims;
        this.admins = admins;
    }

    /** kind: ARTIST, ORGANIZER, LABEL or FESTIVAL; proofUrl: a link that shows it is them (official site, Instagram) */
    public record ClaimRequest(@NotBlank String kind, @NotBlank @Size(max = 200) String target,
                               @Size(max = 500) @Pattern(regexp = "\\s*|https://\\S+", message = "must be an https:// address") String proofUrl,
                               @Size(max = 1000) String message) {
    }

    public record Decision(boolean approve, @Size(max = 500) String reason) {
    }

    /** status APPROVED when the email proved it at once, else PENDING (an admin decides) */
    @PostMapping("/api/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public PageClaims.Claim claim(@Valid @RequestBody ClaimRequest request, Authentication authentication) {
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
