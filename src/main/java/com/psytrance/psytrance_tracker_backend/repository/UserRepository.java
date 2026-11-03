package com.psytrance.psytrance_tracker_backend.repository;

import com.psytrance.psytrance_tracker_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findAllByEmailIgnoreCase(String email);
    Optional<User> findByGoogleId(String googleId);
    boolean existsByUsername(String username);
}
