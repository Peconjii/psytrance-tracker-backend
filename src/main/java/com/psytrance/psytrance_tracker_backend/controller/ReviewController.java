package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.ReviewRequest;
import com.psytrance.psytrance_tracker_backend.dto.ReviewResponse;
import com.psytrance.psytrance_tracker_backend.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Needs login (see SecurityConfig). Reviewing the same event again updates the earlier review. */
    @PostMapping
    public ReviewResponse addReview(@Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return reviewService.addOrUpdateReview(authentication.getName(), request);
    }

    @GetMapping("/event/{eventId}")
    public List<ReviewResponse> getReviewsByEvent(@PathVariable String eventId) {
        return reviewService.getReviewsForEvent(eventId);
    }

    /** Its author or an admin; needs login (see SecurityConfig) */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(@PathVariable long id, Authentication authentication) {
        reviewService.delete(id, authentication.getName());
    }

    @GetMapping("/user")
    public List<ReviewResponse> getUserReviews(Authentication authentication) {
        return reviewService.getReviewsByUsername(authentication.getName());
    }
}
