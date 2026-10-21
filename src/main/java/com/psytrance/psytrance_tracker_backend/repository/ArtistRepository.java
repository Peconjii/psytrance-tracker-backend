package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.dto.ArtistSummary;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findBySlug(String slug);

    /** For the admin search: artists from the line-ups whose slug contains this part, by name */
    List<Artist> findTop30BySlugContainingOrderByNameAsc(String slugPart);

    List<Artist> findBySlugIn(Collection<String> slugs);

    /**
     * Artists playing at any of these events whose slug contains {@code slugPart} ("" matches all),
     * with how many of the events each one plays, busiest first.
     */
    @Query("""
            select new com.psytrance.psytrance_tracker_backend.dto.ArtistSummary(a.slug, a.name, count(ae))
            from ArtistEvent ae join ae.artist a
            where ae.eventId in :eventIds and a.slug like concat('%', :slugPart, '%')
            group by a.id, a.slug, a.name
            order by count(ae) desc, a.name asc
            """)
    List<ArtistSummary> findPlayingAt(@Param("eventIds") Collection<Long> eventIds,
                                      @Param("slugPart") String slugPart,
                                      Pageable pageable);
}
