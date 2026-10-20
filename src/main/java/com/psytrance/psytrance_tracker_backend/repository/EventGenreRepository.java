package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.EventGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EventGenreRepository extends JpaRepository<EventGenre, Long> {

    List<EventGenre> findByEventIdIn(Collection<Long> eventIds);

    @Modifying
    @Query("delete from EventGenre eg where eg.eventId = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
