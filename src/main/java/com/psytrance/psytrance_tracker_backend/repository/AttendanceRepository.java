package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * Marks the user as going. Pressing the button twice (or two tabs at once) must not fail,
     * so an existing mark is simply kept instead of breaking the unique constraint.
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into attendances (user_id, event_id, event_name, starts_on, created_at)
            values (:userId, :eventId, :eventName, :startsOn, :createdAt)
            on conflict (user_id, event_id) do nothing
            """)
    void markGoing(@Param("userId") Long userId,
                   @Param("eventId") Long eventId,
                   @Param("eventName") String eventName,
                   @Param("startsOn") LocalDate startsOn,
                   @Param("createdAt") Instant createdAt);

    @Transactional
    @Modifying
    @Query("delete from Attendance a where a.user.id = :userId and a.eventId = :eventId")
    void unmarkGoing(@Param("userId") Long userId, @Param("eventId") Long eventId);

    /** How many people go to each of these events; events nobody goes to are left out. */
    @Query("""
            select a.eventId as eventId, count(a) as total
            from Attendance a
            where a.eventId in :eventIds
            group by a.eventId
            """)
    List<EventCount> countByEventIds(@Param("eventIds") Collection<Long> eventIds);

    /** Which of these events the user is going to. */
    @Query("select a.eventId from Attendance a where a.user.id = :userId and a.eventId in :eventIds")
    List<Long> findEventIdsOfUser(@Param("userId") Long userId, @Param("eventIds") Collection<Long> eventIds);

    List<Attendance> findByUserIdOrderByStartsOnAsc(Long userId);

    /** Everyone going to these events, with the user loaded too (the reminder job needs their email). */
    @Query("select a from Attendance a join fetch a.user where a.eventId in :eventIds")
    List<Attendance> findWithUserByEventIdIn(@Param("eventIds") Collection<Long> eventIds);

    interface EventCount {
        Long getEventId();

        long getTotal();
    }
}
