package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.FavoriteRequest;
import com.psytrance.psytrance_tracker_backend.model.Favorite;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserRepository userRepository;

    public FavoriteController(FavoriteService favoriteService, UserRepository userRepository) {
        this.favoriteService = favoriteService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<Favorite>> getFavorites(@PathVariable Long userId, Authentication authentication) {
        requireOwnership(userId, authentication);
        return ResponseEntity.ok(favoriteService.getFavoritesByUser(userId));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Favorite> addFavorite(
            @PathVariable Long userId,
            @Valid @RequestBody FavoriteRequest request,
            Authentication authentication) {
        requireOwnership(userId, authentication);
        Favorite favorite = favoriteService.addFavorite(userId, request.getEventId(), request.getEventName());
        return ResponseEntity.ok(favorite);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable Long userId,
            @RequestParam String eventId,
            Authentication authentication) {
        requireOwnership(userId, authentication);
        favoriteService.removeFavorite(userId, eventId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ensures the authenticated user is acting on their own favorites.
     * Throws 401 if there is no authentication, 403 if the path userId
     * belongs to a different account.
     */
    private void requireOwnership(Long userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (!user.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own favorites");
        }
    }
}
