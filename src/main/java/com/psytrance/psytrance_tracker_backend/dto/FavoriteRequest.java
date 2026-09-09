package com.psytrance.psytrance_tracker_backend.dto;

public class FavoriteRequest {
    private String eventId;
    private String eventName;

    public FavoriteRequest() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
}