package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.EventSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventSummaryRepository extends JpaRepository<EventSummary, Long> {

    List<EventSummary> findByOrganizerSlug(String organizerSlug);
}
