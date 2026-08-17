package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.model.Favorite;
import com.psytrance.psytrance_tracker_backend.service.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<Favorite>> getFavorites(@PathVariable Long userId) {
        return ResponseEntity.ok(favoriteService.getFavoritesByUser(userId));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Favorite> addFavorite(
            @PathVariable Long userId,
            @RequestParam String eventId,
            @RequestParam String eventName) {
        Favorite favorite = favoriteService.addFavorite(userId, eventId, eventName);
        return ResponseEntity.ok(favorite);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable Long userId,
            @RequestParam String eventId) {
        favoriteService.removeFavorite(userId, eventId);
        return ResponseEntity.noContent().build();
    }
}