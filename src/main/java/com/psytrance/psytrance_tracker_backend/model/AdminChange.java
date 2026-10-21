package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One change an admin made in the admin panel, e.g. the genres of an artist, before and after. */
@Entity
@Table(name = "admin_changes")
public class AdminChange {

    /** What was changed; target says which one (an artist name, an event id...) */
    public static final String ARTIST_GENRES = "ARTIST_GENRES";
    /** Genres an admin set by hand for an event; target is "<event id> · <event name>" */
    public static final String EVENT_GENRES = "EVENT_GENRES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "target", nullable = false, length = 200)
    private String target;

    @Column(name = "before_value", length = 2000)
    private String beforeValue;

    @Column(name = "after_value", length = 2000)
    private String afterValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // The earlier change this one undoes, or null for an ordinary change
    @Column(name = "undo_of")
    private Long undoOf;

    protected AdminChange() {
    }

    public AdminChange(String username, String kind, String target, String beforeValue, String afterValue, Instant createdAt) {
        this.username = username;
        this.kind = kind;
        this.target = target;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.createdAt = createdAt;
    }

    public AdminChange(String username, String kind, String target, String beforeValue, String afterValue, Instant createdAt,
                       Long undoOf) {
        this(username, kind, target, beforeValue, afterValue, createdAt);
        this.undoOf = undoOf;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getKind() {
        return kind;
    }

    public String getTarget() {
        return target;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUndoOf() {
        return undoOf;
    }
}
