package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.ReviewRequest;
import com.psytrance.psytrance_tracker_backend.dto.ReviewResponse;
import com.psytrance.psytrance_tracker_backend.model.Review;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.ReviewRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    public ReviewResponse addOrUpdateReview(String username, ReviewRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Ako je korisnik već ostavio recenziju za ovaj događaj, ažuriramo je (jedna recenzija po žurci)
        Optional<Review> existingReview = reviewRepository.findByEventIdAndUserId(request.getEventId(), user.getId());

        Review review;
        if (existingReview.isPresent()) {
            review = existingReview.get();
            review.setRating(request.getRating());
            review.setComment(request.getComment());
            review.setCreatedAt(LocalDateTime.now());
        } else {
            review = new Review(request.getEventId(), user, request.getRating(), request.getComment());
        }

        Review saved = reviewRepository.save(review);
        return new ReviewResponse(
                saved.getId(),
                saved.getEventId(),
                saved.getUser().getUsername(),
                saved.getRating(),
                saved.getComment(),
                saved.getCreatedAt()
        );
    }

    public List<ReviewResponse> getReviewsForEvent(String eventId) {
        return reviewRepository.findByEventId(eventId).stream()
                .map(r -> new ReviewResponse(
                        r.getId(),
                        r.getEventId(),
                        r.getUser().getUsername(),
                        r.getRating(),
                        r.getComment(),
                        r.getCreatedAt()
                ))
                .toList();
    }

    public List<Review> getReviewsByUsername(String username) {
        return reviewRepository.findByUserUsername(username);
    }
}