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
    /** A line-up word marked as "not an artist", or shown again; target is the word */
    public static final String ARTIST_HIDDEN = "ARTIST_HIDDEN";
    /** Two spellings merged; target is the spelling that was merged away, after is the artist it went into */
    public static final String ARTIST_MERGED = "ARTIST_MERGED";
    /** An admin wrote or handed back an event timetable; target is "<event id> · <event name>" (no undo) */
    public static final String EVENT_TIMETABLE = "EVENT_TIMETABLE";
    /** An artist's profile was written; target is the artist, before/after the filled fields (no undo) */
    public static final String ARTIST_PROFILE = "ARTIST_PROFILE";
    /** The tracks or festivals on an artist's profile were written; after lists them (no undo) */
    public static final String ARTIST_TRACKS = "ARTIST_TRACKS";
    public static final String ARTIST_FESTIVALS = "ARTIST_FESTIVALS";
    /** An account was linked to an artist as its owner, or unlinked; before/after are the usernames (no undo) */
    public static final String ARTIST_OWNER = "ARTIST_OWNER";
    /** An approved request changed the name on an artist's page; before/after are the names (no undo) */
    public static final String ARTIST_NAME = "ARTIST_NAME";
    /** The artist's main role (DJ, PRODUCER, LIVE; empty: guessed) was picked; before/after are the roles (no undo) */
    public static final String ARTIST_ROLE = "ARTIST_ROLE";
    /** An organizer's page was written (no undo) */
    public static final String ORGANIZER_PROFILE = "ORGANIZER_PROFILE";
    /** An account was linked to an organizer as its owner, or unlinked; before/after are the usernames (no undo) */
    public static final String ORGANIZER_OWNER = "ORGANIZER_OWNER";
    /** An event added on our site was approved or taken down; before/after are the statuses (no undo) */
    public static final String OUR_EVENT = "OUR_EVENT";
    /** An approved event added on our site was changed by its sender or an admin; before/after are the names (no undo) */
    public static final String OUR_EVENT_EDITED = "OUR_EVENT_EDITED";
    /** An admin deleted someone's review; before is "rating ★ comment" (no undo) */
    public static final String REVIEW_DELETED = "REVIEW_DELETED";
    /** An admin hid an event from the site or showed it again; before/after are "shown"/"hidden: reason" (no undo) */
    public static final String EVENT_HIDDEN = "EVENT_HIDDEN";
    /** An admin featured an event on the map or took it off; after is "featured: note" or "" (no undo) */
    public static final String EVENT_FEATURED = "EVENT_FEATURED";
    /** A label's page was written (no undo) */
    public static final String LABEL_PROFILE = "LABEL_PROFILE";
    /** An account was linked to a label as its owner, or unlinked; before/after are the usernames (no undo) */
    public static final String LABEL_OWNER = "LABEL_OWNER";
    /** An artist was put on a label's crew or taken off it (approved request); after is "+ name" or "- name" (no undo) */
    public static final String LABEL_CREW = "LABEL_CREW";
    /** A festival's page was written (no undo) */
    public static final String FESTIVAL_PROFILE = "FESTIVAL_PROFILE";
    /** An account was linked to a festival as its owner, or unlinked; before/after are the usernames (no undo) */
    public static final String FESTIVAL_OWNER = "FESTIVAL_OWNER";
    /** An admin gave an account a role or took it away; target is the username, before/after ADMIN, EDITOR or "" (no undo) */
    public static final String USER_ROLE = "USER_ROLE";
    /** An event set as a festival's edition (true), as not one (false), or back to its name (""); target "Festival: event" (no undo) */
    public static final String FESTIVAL_EDITION = "FESTIVAL_EDITION";

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
