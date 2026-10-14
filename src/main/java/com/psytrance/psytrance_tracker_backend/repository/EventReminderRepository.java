package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.EventReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EventReminderRepository extends JpaRepository<EventReminder, Long> {
    List<EventReminder> findByEventIdIn(Collection<Long> eventIds);
}
