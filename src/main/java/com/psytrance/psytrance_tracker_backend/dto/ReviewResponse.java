package com.psytrance.psytrance_tracker_backend.dto;

import java.time.LocalDateTime;

public class ReviewResponse {

    private Long id;
    private String eventId;
    private String username;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

    public ReviewResponse(Long id, String eventId, String username, int rating, String comment, LocalDateTime createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.username = username;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getUsername() { return username; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}