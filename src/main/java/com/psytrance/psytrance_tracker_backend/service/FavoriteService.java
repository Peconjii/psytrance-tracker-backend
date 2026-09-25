package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.Favorite;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.FavoriteRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // ResponseStatusException is turned into a JSON error by GlobalExceptionHandler
    @Transactional
    public Favorite addFavorite(Long userId, String eventId, String eventName) {
        User user = findUser(userId);

        if (favoriteRepository.existsByUserAndEventId(user, eventId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is already in your favorites");
        }

        Favorite favorite = new Favorite(eventId, eventName, user);
        return favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(Long userId, String eventId) {
        User user = findUser(userId);

        Favorite favorite = favoriteRepository.findByUserAndEventId(user, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event is not in your favorites"));

        favoriteRepository.delete(favorite);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}