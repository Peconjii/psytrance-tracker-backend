package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LineupScanRepository extends JpaRepository<LineupScan, Long> {

    /**
     * These events' line-ups are read again soon (after an admin change): marked as read long ago, the artist index
     * reads them first in its next batches. Events whose line-up was never read are read soon anyway.
     */
    default void rereadSoon(Collection<Long> eventIds) {
        List<LineupScan> scans = findAllById(eventIds);
        scans.forEach(scan -> scan.setScannedAt(Instant.EPOCH));
        saveAll(scans);
    }
}
