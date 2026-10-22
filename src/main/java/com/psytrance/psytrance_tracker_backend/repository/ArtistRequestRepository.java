package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.ArtistRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArtistRequestRepository extends JpaRepository<ArtistRequest, Long> {

    List<ArtistRequest> findByArtistSlugAndStatus(String artistSlug, ArtistRequest.Status status);

    /** The admin's list: requests of one status with who asked, newest first */
    @Query("select r from ArtistRequest r join fetch r.user where r.status = :status order by r.createdAt desc")
    List<ArtistRequest> findWithUserByStatus(@Param("status") ArtistRequest.Status status);
}
