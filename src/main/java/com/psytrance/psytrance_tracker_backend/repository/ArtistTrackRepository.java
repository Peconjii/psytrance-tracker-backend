package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.ArtistTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArtistTrackRepository extends JpaRepository<ArtistTrack, Long> {

    List<ArtistTrack> findByArtistSlugOrderByPositionAsc(String artistSlug);

    @Modifying
    @Query("delete from ArtistTrack t where t.artistSlug = :slug")
    void deleteByArtistSlug(@Param("slug") String artistSlug);
}
