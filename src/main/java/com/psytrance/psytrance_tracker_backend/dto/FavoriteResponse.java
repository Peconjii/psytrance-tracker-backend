package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.Favorite;

public record FavoriteResponse(Long id, String eventId, String eventName) {

    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(favorite.getId(), favorite.getEventId(), favorite.getEventName());
    }
}
