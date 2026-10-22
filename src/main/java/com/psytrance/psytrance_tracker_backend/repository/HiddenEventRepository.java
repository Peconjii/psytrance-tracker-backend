package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.HiddenEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface HiddenEventRepository extends JpaRepository<HiddenEvent, Long> {

    @Query("select h.eventId from HiddenEvent h")
    Set<Long> findAllIds();

    List<HiddenEvent> findAllByOrderByHiddenAtDesc();
}
