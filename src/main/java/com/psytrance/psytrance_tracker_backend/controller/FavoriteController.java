package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.FavoriteRequest;
import com.psytrance.psytrance_tracker_backend.dto.FavoriteResponse;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.service.FavoriteService;
import com.psytrance.psytrance_tracker_backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserService userService;

    public FavoriteController(FavoriteService favoriteService, UserService userService) {
        this.favoriteService = favoriteService;
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public List<FavoriteResponse> getFavorites(@PathVariable Long userId, Authentication authentication) {
        requireOwnership(userId, authentication);
        return favoriteService.getFavoritesByUser(userId).stream()
                .map(FavoriteResponse::from)
                .toList();
    }

    @PostMapping("/{userId}")
    public FavoriteResponse addFavorite(@PathVariable Long userId,
                                        @Valid @RequestBody FavoriteRequest request,
                                        Authentication authentication) {
        requireOwnership(userId, authentication);
        return FavoriteResponse.from(favoriteService.addFavorite(userId, request.getEventId(), request.getEventName()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long userId,
                                               @RequestParam String eventId,
                                               Authentication authentication) {
        requireOwnership(userId, authentication);
        favoriteService.removeFavorite(userId, eventId);
        return ResponseEntity.noContent().build();
    }

    /** Users may only read and change their own favorites: 403 if the path belongs to someone else. */
    private void requireOwnership(Long userId, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (!user.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own favorites");
        }
    }
}
