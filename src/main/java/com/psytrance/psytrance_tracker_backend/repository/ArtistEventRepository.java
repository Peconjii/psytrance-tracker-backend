package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.ArtistEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArtistEventRepository extends JpaRepository<ArtistEvent, Long> {

    @Query("select ae.eventId from ArtistEvent ae where ae.artist.id = :artistId")
    List<Long> findEventIdsByArtistId(@Param("artistId") Long artistId);

    /** For each of these artists: how many of their gigs the line-up wrote "live" for, and how many gigs they have */
    @Query("select ae.artist.id, sum(case when ae.live = true then 1 else 0 end), count(ae) from ArtistEvent ae "
            + "where ae.artist.id in :artistIds group by ae.artist.id")
    List<Object[]> countLiveByArtistIds(@Param("artistIds") Collection<Long> artistIds);

    @Modifying
    @Query("delete from ArtistEvent ae where ae.eventId = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);

    @Modifying
    @Query("delete from ArtistEvent ae where ae.artist.id = :artistId")
    void deleteByArtistId(@Param("artistId") Long artistId);
}
