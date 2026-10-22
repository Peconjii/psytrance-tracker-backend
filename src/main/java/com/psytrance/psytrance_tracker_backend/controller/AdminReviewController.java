package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.ReviewResponse;
import com.psytrance.psytrance_tracker_backend.service.ReviewService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admins read the newest reviews of the whole site; deleting one is {@code DELETE /api/reviews/{id}} */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final Admins admins;

    public AdminReviewController(ReviewService reviewService, Admins admins) {
        this.reviewService = reviewService;
        this.admins = admins;
    }

    @GetMapping
    public List<ReviewResponse> newest(Authentication authentication) {
        admins.requireAdmin(authentication);
        return reviewService.newest();
    }
}
