package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.Favorite;
import com.psytrance.psytrance_tracker_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserId(Long userId);
    boolean existsByUserAndEventId(User user, String eventId);
    Optional<Favorite> findByUserAndEventId(User user, String eventId);

    /** Favorites of these events with their users (the reminder job needs the emails), in one query. */
    @Query("select f from Favorite f join fetch f.user where f.eventId in :eventIds")
    List<Favorite> findWithUserByEventIdIn(@Param("eventIds") Collection<String> eventIds);
}