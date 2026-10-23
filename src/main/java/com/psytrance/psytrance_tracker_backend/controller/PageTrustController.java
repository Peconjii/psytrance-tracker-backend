package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.FestivalEditions;
import com.psytrance.psytrance_tracker_backend.service.PageTrust;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Trust in a page's owner (see {@link PageTrust}): admins set it, the owner reads it to know whether their changes
 * wait for an admin. kind is ARTIST, ORGANIZER, LABEL or FESTIVAL; key the slug, the label's name or the festival's id.
 */
@RestController
public class PageTrustController {

    private final PageTrust pageTrust;
    private final FestivalEditions festivalEditions;
    private final Admins admins;

    public PageTrustController(PageTrust pageTrust, FestivalEditions festivalEditions, Admins admins) {
        this.pageTrust = pageTrust;
        this.festivalEditions = festivalEditions;
        this.admins = admins;
    }

    public record TrustChange(@NotBlank String kind, @NotBlank String key, boolean trusted) {
    }

    public record EditionChange(@NotNull Long eventId, Boolean edition) {
    }

    /** {@code PUT /api/admin/page-trust {"kind": "ARTIST", "key": "act-one", "trusted": true}} */
    @PutMapping("/api/admin/page-trust")
    public PageTrust.Trust set(@Valid @RequestBody TrustChange change, Authentication authentication) {
        return pageTrust.set(PageTrust.kind(change.kind()), change.key(), change.trusted(), admins.requireAdmin(authentication));
    }

    /** {@code GET /api/me/page-trust?kind=LABEL&key=TIP Records}: for the page's owner and admins */
    @GetMapping("/api/me/page-trust")
    public PageTrust.Trust get(@RequestParam String kind, @RequestParam String key, Authentication authentication) {
        PageTrust.Trust trust = pageTrust.find(PageTrust.kind(kind), key).orElse(new PageTrust.Trust(null, false));
        if (!authentication.getName().equals(trust.owner()) && !admins.isAdmin(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the page's owner and admins see this");
        }
        return trust;
    }

    /**
     * A trusted festival owner says which events are the festival's editions: {@code PUT /api/festivals/editions?id=ozora
     * {"eventId": 118452, "edition": true}}; false: not one whatever its name says; null: back to going by the name
     */
    @PutMapping("/api/festivals/editions")
    public void setEdition(@RequestParam String id, @Valid @RequestBody EditionChange change, Authentication authentication) {
        if (!pageTrust.trusts(PageTrust.Kind.FESTIVAL, id, authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a trusted owner of the festival and admins mark editions");
        }
        festivalEditions.set(id, change.eventId(), change.edition(), authentication.getName());
    }
}
