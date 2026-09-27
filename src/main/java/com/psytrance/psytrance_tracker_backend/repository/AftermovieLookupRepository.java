package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.AftermovieLookup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AftermovieLookupRepository extends JpaRepository<AftermovieLookup, String> {
}
