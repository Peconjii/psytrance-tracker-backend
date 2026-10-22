package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.ArtistFestival;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArtistFestivalRepository extends JpaRepository<ArtistFestival, Long> {

    List<ArtistFestival> findByArtistSlugOrderByPositionAsc(String artistSlug);

    @Modifying
    @Query("delete from ArtistFestival f where f.artistSlug = :slug")
    void deleteByArtistSlug(@Param("slug") String artistSlug);
}
