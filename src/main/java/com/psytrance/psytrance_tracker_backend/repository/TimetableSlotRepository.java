package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {

    List<TimetableSlot> findByEventIdOrderByPositionAsc(Long eventId);

    boolean existsByEventIdAndSource(Long eventId, String source);

    @Modifying
    @Query("delete from TimetableSlot t where t.eventId = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
