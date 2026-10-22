package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.FestivalPages;
import com.psytrance.psytrance_tracker_backend.service.LabelPages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admins link the owners of labels and festivals, and decide what label owners ask to change in their crew */
@RestController
@RequestMapping("/api/admin")
public class AdminLabelFestivalController {

    private final LabelPages labelPages;
    private final FestivalPages festivalPages;
    private final Admins admins;

    public AdminLabelFestivalController(LabelPages labelPages, FestivalPages festivalPages, Admins admins) {
        this.labelPages = labelPages;
        this.festivalPages = festivalPages;
        this.admins = admins;
    }

    public record Owner(@NotBlank String username) {
    }

    public record Decision(boolean approve) {
    }

    /** {@code PUT /api/admin/labels/owner?label=TIP Records {"username": "tiprecords"}} */
    @PutMapping("/labels/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setLabelOwner(@RequestParam String label, @Valid @RequestBody Owner owner, Authentication authentication) {
        labelPages.setOwner(label, owner.username().strip(), admins.requireAdmin(authentication));
    }

    @DeleteMapping("/labels/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLabelOwner(@RequestParam String label, Authentication authentication) {
        labelPages.removeOwner(label, admins.requireAdmin(authentication));
    }

    @GetMapping("/labels/requests")
    public List<LabelPages.Request> labelRequests(Authentication authentication) {
        admins.requireAdmin(authentication);
        return labelPages.pendingRequests();
    }

    /** {@code POST /api/admin/labels/requests/7 {"approve": true}} */
    @PostMapping("/labels/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decideLabelRequest(@PathVariable long id, @RequestBody Decision decision, Authentication authentication) {
        labelPages.decide(id, decision.approve(), admins.requireAdmin(authentication));
    }

    /** {@code PUT /api/admin/festivals/owner?id=ozora {"username": "ozorafestival"}} */
    @PutMapping("/festivals/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setFestivalOwner(@RequestParam String id, @Valid @RequestBody Owner owner, Authentication authentication) {
        festivalPages.setOwner(id, owner.username().strip(), admins.requireAdmin(authentication));
    }

    @DeleteMapping("/festivals/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFestivalOwner(@RequestParam String id, Authentication authentication) {
        festivalPages.removeOwner(id, admins.requireAdmin(authentication));
    }
}
