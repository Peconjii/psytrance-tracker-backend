package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.Favorite;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.FavoriteRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
    }

    public List<Favorite> getFavoritesByUser(Long userId) {
        return favoriteRepository.findByUserId(userId);
    }

    @Transactional
    public Favorite addFavorite(Long userId, String eventId, String eventName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

        if (favoriteRepository.existsByUserAndEventId(user, eventId)) {
            throw new RuntimeException("Event je već u favoritima");
        }

        Favorite favorite = new Favorite(eventId, eventName, user);
        return favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(Long userId, String eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

        Favorite favorite = favoriteRepository.findByUserAndEventId(user, eventId)
                .orElseThrow(() -> new RuntimeException("Favorit nije pronađen"));

        favoriteRepository.delete(favorite);
    }
}