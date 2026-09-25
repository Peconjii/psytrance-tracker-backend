package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.ReviewRequest;
import com.psytrance.psytrance_tracker_backend.dto.ReviewResponse;
import com.psytrance.psytrance_tracker_backend.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "http://localhost:5173") // Rešava CORS problem za kontroler
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<?> addReview(@Valid @RequestBody ReviewRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "User must be logged in to review"));
        }

        String username = authentication.getName();
        ReviewResponse response = reviewService.addOrUpdateReview(username, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<ReviewResponse>> getReviewsByEvent(@PathVariable String eventId) {
        List<ReviewResponse> reviews = reviewService.getReviewsForEvent(eventId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/user")
    public ResponseEntity<List<ReviewResponse>> getUserReviews(Authentication authentication) {
        // SecurityConfig guarantees an authenticated user reaches this point.
        List<ReviewResponse> userReviews = reviewService.getReviewsByUsername(authentication.getName());
        return ResponseEntity.ok(userReviews);
    }
}