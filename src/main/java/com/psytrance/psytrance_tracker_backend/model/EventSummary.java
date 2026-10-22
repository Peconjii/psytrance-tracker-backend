package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Name, dates and place of an event whose line-up was read, kept for artists' past gigs. */
@Entity
@Table(name = "event_summaries")
public class EventSummary {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "date_start")
    private LocalDate dateStart;

    @Column(name = "date_end")
    private LocalDate dateEnd;

    @Column(name = "town", length = 255)
    private String town;

    @Column(name = "country", length = 255)
    private String country;

    protected EventSummary() {
    }

    public EventSummary(Long eventId, String name, LocalDate dateStart, LocalDate dateEnd, String town, String country) {
        this.eventId = eventId;
        this.name = name;
        this.dateStart = dateStart;
        this.dateEnd = dateEnd;
        this.town = town;
        this.country = country;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDateStart() {
        return dateStart;
    }

    /** The last day of the event: its end, or its start when it has no end */
    public LocalDate getLastDay() {
        return dateEnd != null ? dateEnd : dateStart;
    }

    public String getTown() {
        return town;
    }

    public String getCountry() {
        return country;
    }
}
