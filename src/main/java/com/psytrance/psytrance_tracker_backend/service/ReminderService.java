package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.model.Attendance;
import com.psytrance.psytrance_tracker_backend.model.EventReminder;
import com.psytrance.psytrance_tracker_backend.model.Favorite;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AttendanceRepository;
import com.psytrance.psytrance_tracker_backend.repository.EventReminderRepository;
import com.psytrance.psytrance_tracker_backend.repository.FavoriteRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Emails users a few days before the events they saved or are going to. It runs every hour
 * (see BackgroundJobs) and remembers what it sent, so running it often is safe: everyone gets
 * each reminder once, and a run missed while the free server was asleep is caught up on the next.
 */
@Service
public class ReminderService {

    /** Events starting today or in the next this many days get a reminder. */
    static final int DAYS_AHEAD = 3;

    private final EventService eventService;
    private final FavoriteRepository favoriteRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventReminderRepository reminderRepository;
    private final ReminderMailer mailer;
    private final Clock clock;

    public ReminderService(EventService eventService,
                           FavoriteRepository favoriteRepository,
                           AttendanceRepository attendanceRepository,
                           EventReminderRepository reminderRepository,
                           ReminderMailer mailer,
                           Clock clock) {
        this.eventService = eventService;
        this.favoriteRepository = favoriteRepository;
        this.attendanceRepository = attendanceRepository;
        this.reminderRepository = reminderRepository;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** Sends every reminder that is due and not sent yet. Returns how many emails went out. */
    public int sendDueReminders() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, EventDto> dueEvents = eventService.findUpcoming().stream()
                .filter(event -> event.dateStart() != null
                        && !event.dateStart().isBefore(today)
                        && !event.dateStart().isAfter(today.plusDays(DAYS_AHEAD)))
                .collect(Collectors.toMap(EventDto::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (dueEvents.isEmpty()) {
            return 0;
        }

        // Who cares about which of these events. Favorites store event ids as text, "going" as numbers
        Map<Long, Recipient> recipients = new HashMap<>();
        List<String> idsAsText = dueEvents.keySet().stream().map(String::valueOf).toList();
        for (Favorite favorite : favoriteRepository.findWithUserByEventIdIn(idsAsText)) {
            addInterest(recipients, favorite.getUser(), Long.valueOf(favorite.getEventId()));
        }
        for (Attendance attendance : attendanceRepository.findWithUserByEventIdIn(dueEvents.keySet())) {
            addInterest(recipients, attendance.getUser(), attendance.getEventId());
        }

        Set<SentKey> alreadySent = reminderRepository.findByEventIdIn(dueEvents.keySet()).stream()
                .map(reminder -> new SentKey(reminder.getUserId(), reminder.getEventId()))
                .collect(Collectors.toSet());

        Instant now = clock.instant();
        int emailsSent = 0;
        for (Recipient recipient : recipients.values()) {
            User user = recipient.user();
            if (!user.isEventReminders() || user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            List<EventDto> events = recipient.eventIds().stream()
                    .filter(eventId -> !alreadySent.contains(new SentKey(user.getId(), eventId)))
                    .map(dueEvents::get)
                    .sorted(Comparator.comparing(EventDto::dateStart))
                    .toList();
            if (events.isEmpty()) {
                continue;
            }
            // Recorded only after a successful send, so a failed email is tried again on the next run
            if (mailer.sendReminder(user, events)) {
                reminderRepository.saveAll(events.stream()
                        .map(event -> new EventReminder(user.getId(), event.id(), now))
                        .toList());
                emailsSent++;
            }
        }
        return emailsSent;
    }

    // A user who both saved an event and is going to it still gets it listed once
    private static void addInterest(Map<Long, Recipient> recipients, User user, Long eventId) {
        recipients.computeIfAbsent(user.getId(), id -> new Recipient(user, new LinkedHashSet<>()))
                .eventIds().add(eventId);
    }

    private record Recipient(User user, Set<Long> eventIds) {
    }

    private record SentKey(Long userId, Long eventId) {
    }
}
