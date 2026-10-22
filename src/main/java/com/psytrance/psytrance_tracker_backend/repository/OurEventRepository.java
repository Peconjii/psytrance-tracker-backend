package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OurEventRepository extends JpaRepository<OurEvent, Long> {

    List<OurEvent> findByStatus(String status);

    /** With the account that sent each, for the admin list */
    @Query("select e from OurEvent e join fetch e.submittedBy where e.status = :status order by e.startsAt")
    List<OurEvent> findByStatusWithSubmitter(@Param("status") String status);

    List<OurEvent> findBySubmittedByIdOrderByCreatedAtDesc(Long userId);

    long countBySubmittedByIdAndStatus(Long userId, String status);
}
