package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** A user's "I'm going" mark on an event. */
@Entity
@Table(name = "attendances")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Attendance() {
    }

    // The app adds marks with AttendanceRepository.markGoing, which also handles pressing the button twice
    public Attendance(User user, Long eventId, String eventName, LocalDate startsOn, Instant createdAt) {
        this.user = user;
        this.eventId = eventId;
        this.eventName = eventName;
        this.startsOn = startsOn;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
