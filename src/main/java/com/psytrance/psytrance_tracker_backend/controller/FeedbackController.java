package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.FeedbackDto;
import com.psytrance.psytrance_tracker_backend.dto.FeedbackRequest;
import com.psytrance.psytrance_tracker_backend.model.Feedback;
import com.psytrance.psytrance_tracker_backend.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** The feedback form (anyone, see SecurityConfig) and the admin list of what was sent. */
@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final Admins admins;

    public FeedbackController(FeedbackService feedbackService, Admins admins) {
        this.feedbackService = feedbackService;
        this.admins = admins;
    }

    /** Example: {@code POST /api/feedback {"kind": "CORRECTION", "aboutType": "ARTIST", "aboutRef": "Ajja", "message": "..."}} */
    @PostMapping("/api/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> submit(@Valid @RequestBody FeedbackRequest request, Authentication authentication,
                                      HttpServletRequest httpRequest) {
        feedbackService.submit(request, usernameOrNull(authentication), clientKey(httpRequest));
        return Map.of("message", "Thanks, your feedback was sent");
    }

    /** Example: {@code GET /api/admin/feedback?status=NEW}. All feedback when no status is given; admins only. */
    @GetMapping("/api/admin/feedback")
    public List<FeedbackDto> list(@RequestParam(required = false) Feedback.Status status, Authentication authentication) {
        requireAdmin(authentication);
        return feedbackService.list(status);
    }

    /** Example: {@code PATCH /api/admin/feedback/7 {"status": "DONE"}}; admins only. */
    @PatchMapping("/api/admin/feedback/{id}")
    public FeedbackDto updateStatus(@PathVariable long id, @Valid @RequestBody StatusChange change,
                                    Authentication authentication) {
        requireAdmin(authentication);
        return feedbackService.updateStatus(id, change.status());
    }

    public record StatusChange(@NotNull Feedback.Status status) {
    }

    private void requireAdmin(Authentication authentication) {
        if (!admins.isAdmin(usernameOrNull(authentication))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can see feedback");
        }
    }

    // The form works for guests too; logged-in senders are remembered with their message
    private static String usernameOrNull(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? null
                : authentication.getName();
    }

    // Who is sending, for the per-hour limit. Behind Render's proxy the client's address is the last one
    // the proxy added to X-Forwarded-For; earlier entries can be written by anyone, so they aren't used.
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].strip();
        }
        return request.getRemoteAddr();
    }
}
