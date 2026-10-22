package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.ArtistOwner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistOwnerRepository extends JpaRepository<ArtistOwner, String> {

    List<ArtistOwner> findByUserId(Long userId);
}
