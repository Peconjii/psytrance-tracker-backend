package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;

import java.time.Instant;

/**
 * One line of the admin history: who changed what, from what to what, and when. {@code undoOf} is the change
 * this one undid; {@code undoneBy} the change that undid this one (null while it stands).
 */
public record AdminChangeDto(Long id, String username, String kind, String target, String before, String after,
                             Instant createdAt, Long undoOf, Long undoneBy) {

    public static AdminChangeDto from(AdminChange change, Long undoneBy) {
        return new AdminChangeDto(change.getId(), change.getUsername(), change.getKind(), change.getTarget(),
                change.getBeforeValue(), change.getAfterValue(), change.getCreatedAt(), change.getUndoOf(), undoneBy);
    }
}
