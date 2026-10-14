package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.model.Attendance;
import com.psytrance.psytrance_tracker_backend.model.EventReminder;
import com.psytrance.psytrance_tracker_backend.model.Favorite;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AttendanceRepository;
import com.psytrance.psytrance_tracker_backend.repository.EventReminderRepository;
import com.psytrance.psytrance_tracker_backend.repository.FavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderServiceTest {

    // Wednesday 2026-09-23
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    private EventService eventService;
    private FavoriteRepository favoriteRepository;
    private AttendanceRepository attendanceRepository;
    private EventReminderRepository reminderRepository;
    private ReminderMailer mailer;
    private ReminderService reminderService;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        favoriteRepository = mock(FavoriteRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);
        reminderRepository = mock(EventReminderRepository.class);
        mailer = mock(ReminderMailer.class);
        when(mailer.sendReminder(any(), any())).thenReturn(true);
        reminderService = new ReminderService(eventService, favoriteRepository, attendanceRepository,
                reminderRepository, mailer, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void remindsAboutEventsStartingInTheNextThreeDaysOnly() {
        User milos = user(1L, "milos");
        EventDto saturday = event(10, "2026-09-26");
        EventDto nextWeek = event(11, "2026-09-30");
        when(eventService.findUpcoming()).thenReturn(List.of(saturday, nextWeek));
        when(favoriteRepository.findWithUserByEventIdIn(List.of("10")))
                .thenReturn(List.of(new Favorite("10", "Event 10", milos)));

        int sent = reminderService.sendDueReminders();

        assertThat(sent).isEqualTo(1);
        verify(mailer).sendReminder(milos, List.of(saturday));
    }

    @Test
    void savedAndGoingEventsArriveInOneEmailWithEachEventOnce() {
        User milos = user(1L, "milos");
        EventDto friday = event(10, "2026-09-25");
        EventDto today = event(11, "2026-09-23");
        when(eventService.findUpcoming()).thenReturn(List.of(today, friday));
        when(favoriteRepository.findWithUserByEventIdIn(anyCollection()))
                .thenReturn(List.of(new Favorite("10", "Event 10", milos)));
        when(attendanceRepository.findWithUserByEventIdIn(anyCollection())).thenReturn(List.of(
                going(milos, 10), going(milos, 11)));

        reminderService.sendDueReminders();

        // Soonest first
        verify(mailer).sendReminder(milos, List.of(today, friday));
    }

    @Test
    void nothingIsSentTwice() {
        User milos = user(1L, "milos");
        when(eventService.findUpcoming()).thenReturn(List.of(event(10, "2026-09-24")));
        when(attendanceRepository.findWithUserByEventIdIn(anyCollection())).thenReturn(List.of(going(milos, 10)));
        when(reminderRepository.findByEventIdIn(anyCollection()))
                .thenReturn(List.of(new EventReminder(1L, 10L, NOW.minusSeconds(3600))));

        assertThat(reminderService.sendDueReminders()).isZero();
        verify(mailer, never()).sendReminder(any(), any());
    }

    @Test
    void sentRemindersAreRecorded() {
        User milos = user(1L, "milos");
        when(eventService.findUpcoming()).thenReturn(List.of(event(10, "2026-09-24")));
        when(attendanceRepository.findWithUserByEventIdIn(anyCollection())).thenReturn(List.of(going(milos, 10)));

        reminderService.sendDueReminders();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventReminder>> saved = ArgumentCaptor.forClass(List.class);
        verify(reminderRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(reminder -> {
            assertThat(reminder.getUserId()).isEqualTo(1L);
            assertThat(reminder.getEventId()).isEqualTo(10L);
            assertThat(reminder.getSentAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aFailedEmailIsNotRecordedSoItIsTriedAgain() {
        User milos = user(1L, "milos");
        when(eventService.findUpcoming()).thenReturn(List.of(event(10, "2026-09-24")));
        when(attendanceRepository.findWithUserByEventIdIn(anyCollection())).thenReturn(List.of(going(milos, 10)));
        when(mailer.sendReminder(eq(milos), any())).thenReturn(false);

        assertThat(reminderService.sendDueReminders()).isZero();
        verify(reminderRepository, never()).saveAll(any());
    }

    @Test
    void usersWhoTurnedRemindersOffGetNothing() {
        User milos = user(1L, "milos");
        milos.setEventReminders(false);
        when(eventService.findUpcoming()).thenReturn(List.of(event(10, "2026-09-24")));
        when(attendanceRepository.findWithUserByEventIdIn(anyCollection())).thenReturn(List.of(going(milos, 10)));

        assertThat(reminderService.sendDueReminders()).isZero();
        verify(mailer, never()).sendReminder(any(), any());
    }

    @Test
    void noDatabaseWorkWhenNoEventIsComingUp() {
        when(eventService.findUpcoming()).thenReturn(List.of(event(10, "2026-10-20")));

        assertThat(reminderService.sendDueReminders()).isZero();
        verify(favoriteRepository, never()).findWithUserByEventIdIn(any());
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }

    private static Attendance going(User user, long eventId) {
        return new Attendance(user, eventId, "Event " + eventId, null, NOW);
    }

    private static EventDto event(long id, String startDate) {
        return EventDto.from(new GoabaseParty(id, "Event " + id, startDate + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Serbia", "Novi Sad", 45.25, 19.84, null, null));
    }
}
