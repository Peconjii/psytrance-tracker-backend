package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    private LocalDateTime createdAt;

    // Null until the review is edited for the first time
    private LocalDateTime updatedAt;

    protected Review() {
    }

    public Review(String eventId, User user) {
        this.eventId = eventId;
        this.user = user;
    }

    // JPA calls these right before the INSERT / UPDATE, so the service never sets timestamps by hand
    @PrePersist
    void onCreate() {
        createdAt = now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = now();
    }

    // PostgreSQL keeps microseconds; cutting to that precision keeps the saved and returned values identical
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public User getUser() {
        return user;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
