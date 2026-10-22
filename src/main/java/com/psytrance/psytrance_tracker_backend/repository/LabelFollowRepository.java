package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.LabelFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LabelFollowRepository extends JpaRepository<LabelFollow, Long> {

    List<LabelFollow> findByUserIdOrderByLabelNameAsc(Long userId);

    Optional<LabelFollow> findByUserIdAndLabelName(Long userId, String labelName);

    /** Every label follow with its user (the new-gig job needs the emails), in one query. */
    @Query("select f from LabelFollow f join fetch f.user")
    List<LabelFollow> findAllWithUser();
}
