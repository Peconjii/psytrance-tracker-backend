package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.ArtistFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArtistFollowRepository extends JpaRepository<ArtistFollow, Long> {

    List<ArtistFollow> findByUserIdOrderByArtistNameAsc(Long userId);

    Optional<ArtistFollow> findByUserIdAndArtistSlug(Long userId, String artistSlug);

    long countByUserId(Long userId);

    long countByArtistSlug(String artistSlug);

    /** Every follow with its user (the new-gig job needs the emails), in one query. */
    @Query("select f from ArtistFollow f join fetch f.user")
    List<ArtistFollow> findAllWithUser();
}
