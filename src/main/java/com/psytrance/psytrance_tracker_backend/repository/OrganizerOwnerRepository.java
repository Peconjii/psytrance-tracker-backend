package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.OrganizerOwner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizerOwnerRepository extends JpaRepository<OrganizerOwner, String> {

    List<OrganizerOwner> findByUserId(Long userId);
}
