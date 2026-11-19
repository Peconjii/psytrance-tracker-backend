package com.psytrance.psytrance_tracker_backend.service.events;

import com.psytrance.psytrance_tracker_backend.dto.Buddies;
import com.psytrance.psytrance_tracker_backend.dto.BuddySettings;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AttendanceRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Festival Buddies: who else is going to an event. Private by default and mutual: you only see the others
 * if you are going yourself, let them see you too and confirmed your email. Nobody's email is ever shown; Instagram is
 * the only contact.
 */
@Service
public class BuddyService {

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public BuddyService(AttendanceRepository attendanceRepository, UserRepository userRepository) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Buddies of(long eventId, String username) {
        List<User> visible = attendanceRepository.findVisibleBuddies(eventId);
        long count = visible.size();
        if (username == null) {
            return new Buddies(count, false, "login", List.of());
        }
        User me = userRepository.findByUsername(username).orElse(null);
        if (me == null) {
            return new Buddies(count, false, "login", List.of());
        }
        boolean going = !attendanceRepository.findEventIdsOfUser(me.getId(), List.of(eventId)).isEmpty();
        if (!going) {
            return new Buddies(count, false, "not-going", List.of());
        }
        // A confirmed email makes it costly to make throwaway accounts just to collect people's Instagram names
        if (!me.isEmailVerified()) {
            return new Buddies(count, false, "verify-email", List.of());
        }
        if (!me.isBuddyVisible()) {
            return new Buddies(count, false, "not-visible", List.of());
        }
        List<Buddies.Buddy> list = visible.stream()
                .sorted(Comparator.comparing((User u) -> !u.getId().equals(me.getId()))
                        .thenComparing(u -> u.getUsername().toLowerCase(Locale.ROOT)))
                .map(u -> new Buddies.Buddy(u.getUsername(), u.getCountry(), u.getBuddyNote(), u.getInstagram(),
                        u.getId().equals(me.getId())))
                .toList();
        return new Buddies(count, true, null, list);
    }

    @Transactional(readOnly = true)
    public BuddySettings settingsOf(String username) {
        User user = findUser(username);
        return new BuddySettings(user.isBuddyVisible(), user.getCountry(), user.getBuddyNote(), user.getInstagram());
    }

    @Transactional
    public BuddySettings save(String username, BuddySettings settings) {
        if (settings.country() != null && !COUNTRIES.contains(settings.country())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown country");
        }
        User user = findUser(username);
        user.setBuddyVisible(settings.visible());
        user.setCountry(settings.country());
        user.setBuddyNote(blankToNull(settings.note()));
        user.setInstagram(blankToNull(settings.instagram()));
        return settingsOf(username);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
