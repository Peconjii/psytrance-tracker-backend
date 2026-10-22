package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import com.psytrance.psytrance_tracker_backend.model.EventGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface EventGenreRepository extends JpaRepository<EventGenre, Long> {

    List<EventGenre> findByEventIdIn(Collection<Long> eventIds);

    boolean existsByEventIdAndSource(Long eventId, GenreShare.Source source);

    /** The events that have this map genre, for the genre filter on the events page */
    @Query("select distinct eg.eventId from EventGenre eg where eg.genreId = :genreId")
    Set<Long> findEventIdsByGenreId(@Param("genreId") String genreId);

    @Modifying
    @Query("delete from EventGenre eg where eg.eventId = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
