package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Proof that a user was already emailed about an event, so the reminder job never sends it twice. */
@Entity
@Table(name = "event_reminders")
public class EventReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected EventReminder() {
    }

    public EventReminder(Long userId, Long eventId, Instant sentAt) {
        this.userId = userId;
        this.eventId = eventId;
        this.sentAt = sentAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
