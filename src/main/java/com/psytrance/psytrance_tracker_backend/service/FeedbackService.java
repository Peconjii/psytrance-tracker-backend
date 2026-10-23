package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.FeedbackDto;
import com.psytrance.psytrance_tracker_backend.dto.FeedbackRequest;
import com.psytrance.psytrance_tracker_backend.model.Feedback;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.FeedbackRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

/**
 * The feedback form. Anyone can send, so it is guarded against spam without a captcha: a hidden
 * field only bots fill in, and a limit of a few messages per hour from one address.
 */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final FeedbackReplyMailer replyMailer;
    private final FormLimits formLimits;
    private final Clock clock;
    private final int maxPerHour;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           UserRepository userRepository,
                           FeedbackReplyMailer replyMailer,
                           FormLimits formLimits,
                           Clock clock,
                           @Value("${app.feedback.max-per-hour:5}") int maxPerHour) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.replyMailer = replyMailer;
        this.formLimits = formLimits;
        this.clock = clock;
        this.maxPerHour = maxPerHour;
    }

    /**
     * Stores the feedback, unless the hidden field is filled in (a bot): then it is dropped without
     * telling, so the bot has no reason to try another way. Too many messages from one address is a 429.
     */
    public void submit(FeedbackRequest request, String username, String clientKey) {
        if (FormLimits.isBot(request.website())) {
            return;
        }
        formLimits.count("feedback", clientKey, maxPerHour, Duration.ofHours(1),
                "That's a lot of feedback in a short time. Please try again in an hour.");
        Instant now = clock.instant();
        Long userId = username == null ? null : userRepository.findByUsername(username).map(User::getId).orElse(null);
        feedbackRepository.save(new Feedback(request.kind(), request.aboutType(), blankToNull(request.aboutRef()),
                blankToNull(request.pageUrl()), request.message().strip(), blankToNull(request.email()), userId, now));
    }

    /** Newest first; all of them when status is null. */
    public List<FeedbackDto> list(Feedback.Status status) {
        List<Feedback> feedback = status == null
                ? feedbackRepository.findAllByOrderByCreatedAtDesc()
                : feedbackRepository.findByStatusOrderByCreatedAtDesc(status);
        Map<Long, User> users = userRepository.findAllById(feedback.stream()
                        .map(Feedback::getUserId).filter(id -> id != null).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return feedback.stream().map(f -> dto(f, users.get(f.getUserId()))).toList();
    }

    /**
     * Emails an admin's answer to the sender and keeps it with the message. 409 when there is no address to answer;
     * a failed email is a 502 and nothing is saved, so the admin can try again.
     */
    @Transactional
    public FeedbackDto reply(long id, String text, String adminUsername) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        User user = feedback.getUserId() == null ? null : userRepository.findById(feedback.getUserId()).orElse(null);
        String to = replyTo(feedback, user);
        if (to == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This message has no email address to answer");
        }
        try {
            replyMailer.send(to, text.strip(), feedback.getMessage());
        } catch (MailException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The email could not be sent. Try again later.", e);
        }
        feedback.replied(text.strip(), adminUsername, clock.instant());
        return dto(feedback, user);
    }

    private static FeedbackDto dto(Feedback feedback, User user) {
        return FeedbackDto.from(feedback, user == null ? null : user.getUsername(), replyTo(feedback, user));
    }

    // The address written in the form, else the account's
    private static String replyTo(Feedback feedback, User user) {
        if (feedback.getEmail() != null && !feedback.getEmail().isBlank()) {
            return feedback.getEmail();
        }
        return user == null || user.getEmail() == null || user.getEmail().isBlank() ? null : user.getEmail();
    }

    @Transactional
    public FeedbackDto updateStatus(long id, Feedback.Status status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        feedback.setStatus(status);
        return dto(feedback, feedback.getUserId() == null ? null : userRepository.findById(feedback.getUserId()).orElse(null));
    }
}
