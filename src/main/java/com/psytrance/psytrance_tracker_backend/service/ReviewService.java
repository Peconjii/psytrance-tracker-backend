package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.ReviewRequest;
import com.psytrance.psytrance_tracker_backend.dto.ReviewResponse;
import com.psytrance.psytrance_tracker_backend.model.Review;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.ReviewRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    /** One review per user and event: a second review of the same event replaces the first. */
    @Transactional
    public ReviewResponse addOrUpdateReview(String username, ReviewRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        Review review = reviewRepository.findByEventIdAndUserId(request.getEventId(), user.getId())
                .orElseGet(() -> new Review(request.getEventId(), user));
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        return ReviewResponse.from(reviewRepository.save(review));
    }

    public List<ReviewResponse> getReviewsForEvent(String eventId) {
        return reviewRepository.findByEventId(eventId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    public List<ReviewResponse> getReviewsByUsername(String username) {
        return reviewRepository.findByUserUsername(username).stream()
                .map(ReviewResponse::from)
                .toList();
    }
}
