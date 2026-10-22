package com.psytrance.psytrance_tracker_backend.model;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * An event added on our site instead of Goabase. Once approved it is shown as a Goabase party (see {@link #toDetails()}),
 * so everything that reads Goabase parties (line-up index, genres, ships, event page) works for it unchanged.
 */
@Entity
@Table(name = "our_events")
public class OurEvent {

    /** Our events are listed with this added to their id, so they never collide with Goabase's ids */
    public static final long ID_BASE = 1_000_000_000L;

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    // On the venue's clock, in timeZone
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, length = 100)
    private String town;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "line_up")
    private String lineUp;

    @Column(name = "location_text")
    private String locationText;

    @Column(name = "entry_fee", length = 500)
    private String entryFee;

    private String info;

    @Column(name = "organizer_name", length = 200)
    private String organizerName;

    @Column(name = "organizer_url", length = 500)
    private String organizerUrl;

    @Column(name = "ticket_url", length = 500)
    private String ticketUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // Map genre ids, comma-separated
    @Column(nullable = false, length = 200)
    private String genres = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    protected OurEvent() {
    }

    public OurEvent(User submittedBy, Instant createdAt) {
        this.submittedBy = submittedBy;
        this.createdAt = createdAt;
        this.status = PENDING;
    }

    /** The id the event has in lists, on the map and on its page */
    public long eventId() {
        return ID_BASE + id;
    }

    public static boolean isOurs(long eventId) {
        return eventId > ID_BASE;
    }

    /** The event as the Goabase list sends a party */
    public GoabaseParty toParty() {
        return toDetails().summary();
    }

    /** The event as Goabase's single-party endpoint sends it; the page link is the ticket (or info) link */
    public GoabasePartyDetails toDetails() {
        ZoneId zone = ZoneId.of(timeZone);
        String start = startsAt.atZone(zone).toOffsetDateTime().toString();
        String end = endsAt == null ? null : endsAt.atZone(zone).toOffsetDateTime().toString();
        return new GoabasePartyDetails(eventId(), name, start, end, eventType, "Scheduled", country, town, lat, lon,
                imageUrl, imageUrl, ticketUrl, lineUp, locationText, entryFee, info, organizerName, organizerUrl);
    }

    public void approve(String username, Instant now) {
        status = APPROVED;
        decidedBy = username;
        decidedAt = now;
        rejectReason = null;
    }

    public void reject(String username, String reason, Instant now) {
        status = REJECTED;
        decidedBy = username;
        decidedAt = now;
        rejectReason = reason;
    }

    public List<String> genreIds() {
        return genres.isBlank() ? List.of() : Arrays.asList(genres.split(","));
    }

    public void setDetails(String name, String eventType, LocalDateTime startsAt, LocalDateTime endsAt, String timeZone,
                           String country, String town, double lat, double lon, String lineUp, String locationText,
                           String entryFee, String info, String organizerName, String organizerUrl, String ticketUrl,
                           String imageUrl, List<String> genreIds) {
        this.name = name;
        this.eventType = eventType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.timeZone = timeZone;
        this.country = country;
        this.town = town;
        this.lat = lat;
        this.lon = lon;
        this.lineUp = lineUp;
        this.locationText = locationText;
        this.entryFee = entryFee;
        this.info = info;
        this.organizerName = organizerName;
        this.organizerUrl = organizerUrl;
        this.ticketUrl = ticketUrl;
        this.imageUrl = imageUrl;
        this.genres = String.join(",", genreIds);
    }

    public Long getId() {
        return id;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public String getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getCountry() {
        return country;
    }

    public String getTown() {
        return town;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String getLineUp() {
        return lineUp;
    }

    public String getLocationText() {
        return locationText;
    }

    public String getEntryFee() {
        return entryFee;
    }

    public String getInfo() {
        return info;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public String getOrganizerUrl() {
        return organizerUrl;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
