package com.psytrance.psytrance_tracker_backend.service.site;

import com.psytrance.psytrance_tracker_backend.service.mail.FeedbackReplyMailer;
import com.psytrance.psytrance_tracker_backend.service.events.EventGenreAdmin;
import com.psytrance.psytrance_tracker_backend.service.map.GalaxyContent;
import com.psytrance.psytrance_tracker_backend.service.map.MapGenres;
import com.psytrance.psytrance_tracker_backend.service.map.MapPlacements;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final MapGenres mapGenres;
    private final MapPlacements placements;
    private final GalaxyContent galaxyContent;
    private final EventGenreAdmin eventGenreAdmin;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           UserRepository userRepository,
                           FeedbackReplyMailer replyMailer,
                           FormLimits formLimits,
                           Clock clock,
                           @Value("${app.feedback.max-per-hour:5}") int maxPerHour,
                           MapGenres mapGenres,
                           MapPlacements placements,
                           GalaxyContent galaxyContent,
                           EventGenreAdmin eventGenreAdmin) {
        this.eventGenreAdmin = eventGenreAdmin;
        this.mapGenres = mapGenres;
        this.placements = placements;
        this.galaxyContent = galaxyContent;
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

    private FeedbackDto dto(Feedback feedback, User user) {
        return FeedbackDto.from(feedback, user == null ? null : user.getUsername(), replyTo(feedback, user),
                accept(feedback).orElse(null));
    }

    // A track goes to one of the artist's genres, the one the sender was looking at first; an artist to the genre
    // it was suggested for, unless they already orbit it
    private Optional<FeedbackDto.Accept> accept(Feedback feedback) {
        return FeedbackSuggestions.of(feedback, mapGenres).map(s -> {
            List<String> genres = new ArrayList<>();
            if (s.type().equals("EVENT_GENRE")) {
                genres.addAll(List.of(s.genreId().split(",")));
                return new FeedbackDto.Accept(s.type(), null, s.title(), null, null, genres);
            }
            if (s.type().equals("TRACK")) {
                List<String> artistGenres = placements.genresOf(s.artist());
                FeedbackSuggestions.genreOfPage(feedback.getPageUrl())
                        .filter(g -> artistGenres.contains(g) || (artistGenres.isEmpty() && mapGenres.isGenre(g)))
                        .ifPresent(genres::add);
                artistGenres.stream().filter(g -> !genres.contains(g)).forEach(genres::add);
            } else if (!placements.genresOf(s.artist()).contains(s.genreId())) {
                genres.add(s.genreId());
            }
            return new FeedbackDto.Accept(s.type(), s.artist(), s.title(), s.year(), s.youtube(), genres);
        });
    }

    /**
     * Adds a suggested track to a genre's key tracks, or a suggested artist to a genre's star, and marks the message
     * done. Logged in the admin history like a change by hand, so it can be undone there. 409 when it is already
     * there or the message is not a suggestion that can be added.
     */
    @Transactional
    public FeedbackDto accept(long id, String genreId, String adminUsername) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        FeedbackDto.Accept accept = accept(feedback)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "This message is not a track or artist suggestion"));
        if (accept.type().equals("EVENT_GENRE")) {
            // All the genres the sender named, set by hand on the event (Admin -> Events can change them again)
            eventGenreAdmin.setGenres(Long.parseLong(accept.title()), accept.genres(), adminUsername);
            feedback.setStatus(Feedback.Status.DONE);
            return dto(feedback, feedback.getUserId() == null ? null : userRepository.findById(feedback.getUserId()).orElse(null));
        }
        String genre = genreId == null || genreId.isBlank() ? (accept.genres().isEmpty() ? null : accept.genres().get(0)) : genreId;
        if (genre == null || !accept.genres().contains(genre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, accept.type().equals("TRACK")
                    ? accept.artist() + " is not on that genre's star. Put them on the map first (Admin -> Artists)."
                    : accept.artist() + " is already in that genre.");
        }
        if (accept.type().equals("TRACK")) {
            GalaxyContent.Content content = galaxyContent.get(genre);
            boolean there = content.tracks().stream().anyMatch(t -> t.artist().equalsIgnoreCase(accept.artist())
                    && t.title().equalsIgnoreCase(accept.title()));
            if (there) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + accept.title() + "\" is already a key track there");
            }
            List<GalaxyContent.Track> tracks = new ArrayList<>(content.tracks());
            tracks.add(new GalaxyContent.Track(accept.artist(), accept.title(), accept.year(), accept.youtube(), false, false, null));
            galaxyContent.save(genre, new GalaxyContent.Content(content.description(), content.bass(), content.kick(),
                    content.atmosphere(), content.year(), content.bpmMin(), content.bpmMax(), content.verified(), tracks),
                    adminUsername);
        } else {
            List<String> wanted = new ArrayList<>(placements.genresOf(accept.artist()));
            wanted.add(genre);
            placements.setGenres(accept.artist(), wanted, adminUsername);
        }
        feedback.setStatus(Feedback.Status.DONE);
        return dto(feedback, feedback.getUserId() == null ? null : userRepository.findById(feedback.getUserId()).orElse(null));
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
