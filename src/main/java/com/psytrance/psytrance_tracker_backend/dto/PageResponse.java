package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/**
 * One page of results plus what the client needs to ask for the next one.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /** Cuts page number {@code page} (0-based) of {@code size} items out of the full list. */
    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int total = all.size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, total);
        int totalPages = (total + size - 1) / size;
        return new PageResponse<>(List.copyOf(all.subList(from, to)), page, size, total, totalPages, to < total);
    }
}
