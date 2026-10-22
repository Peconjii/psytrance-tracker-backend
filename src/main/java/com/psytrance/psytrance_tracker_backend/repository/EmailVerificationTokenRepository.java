package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.EmailVerificationToken;
import com.psytrance.psytrance_tracker_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    Optional<EmailVerificationToken> findFirstByUserOrderByCreatedAtDesc(User user);

    /** One query, so a link opened twice at once doesn't fail on the second delete (as deleteBy would) */
    @Modifying
    @Query("delete from EmailVerificationToken t where t.user = :user")
    void deleteByUser(@Param("user") User user);
}
