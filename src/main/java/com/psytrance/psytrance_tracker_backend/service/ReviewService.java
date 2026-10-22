package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.ReviewRequest;
import com.psytrance.psytrance_tracker_backend.dto.ReviewResponse;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.Review;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.ReviewRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final Admins admins;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository, Admins admins,
                         AdminChangeRepository changeRepository, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.admins = admins;
        this.changeRepository = changeRepository;
        this.clock = clock;
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

    /** The newest reviews of the whole site, for the admins' list */
    public List<ReviewResponse> newest() {
        return reviewRepository.findTop100ByOrderByIdDesc().stream()
                .map(ReviewResponse::from)
                .toList();
    }

    /**
     * Deletes a review: its author may, and admins may delete anyone's (spam, insults). An admin deleting someone
     * else's review is kept in the history with what it said.
     */
    @Transactional
    public void delete(long id, String username) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No review " + id));
        String author = review.getUser().getUsername();
        boolean own = author.equals(username);
        if (!own && !admins.isEditor(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only its author, editors and admins can delete a review");
        }
        if (!own) {
            String said = review.getRating() + " ★ " + (review.getComment() == null ? "" : review.getComment());
            changeRepository.save(new AdminChange(username, AdminChange.REVIEW_DELETED,
                    "@" + author + " on event #" + review.getEventId(), said, "", clock.instant()));
        }
        reviewRepository.delete(review);
    }

    public List<ReviewResponse> getReviewsByUsername(String username) {
        return reviewRepository.findByUserUsername(username).stream()
                .map(ReviewResponse::from)
                .toList();
    }
}
