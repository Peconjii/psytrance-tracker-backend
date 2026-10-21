package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.FollowNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FollowNoticeRepository extends JpaRepository<FollowNotice, Long> {

    List<FollowNotice> findByUserId(Long userId);

    List<FollowNotice> findByEventIdIn(Collection<Long> eventIds);
}
