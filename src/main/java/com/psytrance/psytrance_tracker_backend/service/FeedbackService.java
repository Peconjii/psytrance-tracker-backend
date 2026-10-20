package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.FeedbackDto;
import com.psytrance.psytrance_tracker_backend.dto.FeedbackRequest;
import com.psytrance.psytrance_tracker_backend.model.Feedback;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.FeedbackRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The feedback form. Anyone can send, so it is guarded against spam without a captcha: a hidden
 * field only bots fill in, and a limit of a few messages per hour from one address.
 */
@Service
public class FeedbackService {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final int maxPerHour;

    // When each address last sent feedback, within the last hour. In memory: a restart forgets it, which is fine
    private final Map<String, Deque<Instant>> recentByClient = new ConcurrentHashMap<>();

    public FeedbackService(FeedbackRepository feedbackRepository,
                           UserRepository userRepository,
                           Clock clock,
                           @Value("${app.feedback.max-per-hour:5}") int maxPerHour) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxPerHour = maxPerHour;
    }

    /**
     * Stores the feedback, unless the hidden field is filled in (a bot): then it is dropped without
     * telling, so the bot has no reason to try another way. Too many messages from one address is a 429.
     */
    public void submit(FeedbackRequest request, String username, String clientKey) {
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }
        Instant now = clock.instant();
        Deque<Instant> recent = recentByClient.computeIfAbsent(clientKey, key -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(WINDOW))) {
                recent.pollFirst();
            }
            if (recent.size() >= maxPerHour) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "That's a lot of feedback in a short time. Please try again in an hour.");
            }
            recent.addLast(now);
        }
        Long userId = username == null ? null : userRepository.findByUsername(username).map(User::getId).orElse(null);
        feedbackRepository.save(new Feedback(request.kind(), request.aboutType(), blankToNull(request.aboutRef()),
                blankToNull(request.pageUrl()), request.message().strip(), blankToNull(request.email()), userId, now));
    }

    /** Newest first; all of them when status is null. */
    public List<FeedbackDto> list(Feedback.Status status) {
        List<Feedback> feedback = status == null
                ? feedbackRepository.findAllByOrderByCreatedAtDesc()
                : feedbackRepository.findByStatusOrderByCreatedAtDesc(status);
        Map<Long, String> usernames = userRepository.findAllById(feedback.stream()
                        .map(Feedback::getUserId).filter(id -> id != null).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return feedback.stream().map(f -> FeedbackDto.from(f, usernames.get(f.getUserId()))).toList();
    }

    @Transactional
    public FeedbackDto updateStatus(long id, Feedback.Status status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        feedback.setStatus(status);
        String username = feedback.getUserId() == null ? null
                : userRepository.findById(feedback.getUserId()).map(User::getUsername).orElse(null);
        return FeedbackDto.from(feedback, username);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
