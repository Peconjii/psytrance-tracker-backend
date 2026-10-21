package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A gig a follower already knows about, so the new-gig email never lists it twice. */
@Entity
@Table(name = "follow_notices")
public class FollowNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "noted_at", nullable = false)
    private Instant notedAt;

    protected FollowNotice() {
    }

    public FollowNotice(Long userId, Long eventId, Instant notedAt) {
        this.userId = userId;
        this.eventId = eventId;
        this.notedAt = notedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEventId() {
        return eventId;
    }
}
