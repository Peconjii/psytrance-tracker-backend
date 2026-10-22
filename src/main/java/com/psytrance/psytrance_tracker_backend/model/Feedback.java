package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A message sent through the feedback form: a data correction, complaint, suggestion or idea. */
@Entity
@Table(name = "feedback")
public class Feedback {

    public enum Kind { CORRECTION, COMPLAINT, SUGGESTION, IDEA, OTHER }

    /** What the feedback is about, when it was sent from a genre, artist, event... page */
    public enum About { GENRE, ARTIST, EVENT, FESTIVAL, LABEL, PAGE }

    public enum Status { NEW, IN_PROGRESS, DONE, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "about_type", length = 20)
    private About aboutType;

    @Column(name = "about_ref", length = 200)
    private String aboutRef;

    @Column(name = "page_url", length = 500)
    private String pageUrl;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "email")
    private String email;

    // Null for guests
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // An admin's answer, emailed to the sender
    @Column(name = "reply_text", length = 3000)
    private String replyText;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "replied_by")
    private String repliedBy;

    protected Feedback() {
    }

    public Feedback(Kind kind, About aboutType, String aboutRef, String pageUrl, String message, String email,
                    Long userId, Instant createdAt) {
        this.kind = kind;
        this.aboutType = aboutType;
        this.aboutRef = aboutRef;
        this.pageUrl = pageUrl;
        this.message = message;
        this.email = email;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public About getAboutType() {
        return aboutType;
    }

    public String getAboutRef() {
        return aboutRef;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public String getMessage() {
        return message;
    }

    public String getEmail() {
        return email;
    }

    public Long getUserId() {
        return userId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReplyText() {
        return replyText;
    }

    public Instant getRepliedAt() {
        return repliedAt;
    }

    public String getRepliedBy() {
        return repliedBy;
    }

    public void replied(String text, String admin, Instant at) {
        this.replyText = text;
        this.repliedBy = admin;
        this.repliedAt = at;
    }
}
