package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.MapArtistGenre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapArtistGenreRepository extends JpaRepository<MapArtistGenre, Long> {

    /** Every placement in the order it was added, which is the order satellites orbit a star */
    List<MapArtistGenre> findAllByOrderByIdAsc();
}
