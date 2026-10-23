package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.FeedbackDto;
import com.psytrance.psytrance_tracker_backend.dto.FeedbackRequest;
import com.psytrance.psytrance_tracker_backend.model.Feedback;
import com.psytrance.psytrance_tracker_backend.service.FeedbackService;
import com.psytrance.psytrance_tracker_backend.util.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.psytrance.psytrance_tracker_backend.util.Logins.usernameOrNull;

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
        feedbackService.submit(request, usernameOrNull(authentication), ClientAddress.of(httpRequest));
        return Map.of("message", "Thanks, your feedback was sent");
    }

    /** Example: {@code GET /api/admin/feedback?status=NEW}. All feedback when no status is given; admins only. */
    @GetMapping("/api/admin/feedback")
    public List<FeedbackDto> list(@RequestParam(required = false) Feedback.Status status, Authentication authentication) {
        admins.requireAdmin(authentication);
        return feedbackService.list(status);
    }

    /** Example: {@code PATCH /api/admin/feedback/7 {"status": "DONE"}}; admins only. */
    @PatchMapping("/api/admin/feedback/{id}")
    public FeedbackDto updateStatus(@PathVariable long id, @Valid @RequestBody StatusChange change,
                                    Authentication authentication) {
        admins.requireAdmin(authentication);
        return feedbackService.updateStatus(id, change.status());
    }

    public record StatusChange(@NotNull Feedback.Status status) {
    }

    /** Example: {@code POST /api/admin/feedback/7/reply {"text": "Thanks, fixed!"}}: emailed to the sender; admins only */
    @PostMapping("/api/admin/feedback/{id}/reply")
    public FeedbackDto reply(@PathVariable long id, @Valid @RequestBody Reply reply, Authentication authentication) {
        return feedbackService.reply(id, reply.text(), admins.requireAdmin(authentication));
    }

    public record Reply(@NotBlank @Size(max = 3000) String text) {
    }
}
