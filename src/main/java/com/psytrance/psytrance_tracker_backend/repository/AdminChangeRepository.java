package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminChangeRepository extends JpaRepository<AdminChange, Long> {
}
