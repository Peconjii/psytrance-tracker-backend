package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminChangeRepository extends JpaRepository<AdminChange, Long> {

    /** The history page: the newest changes first */
    List<AdminChange> findTop200ByOrderByCreatedAtDescIdDesc();

    boolean existsByUndoOf(Long undoOf);
}
