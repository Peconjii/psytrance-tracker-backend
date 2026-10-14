package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.GoingEvent;
import com.psytrance.psytrance_tracker_backend.dto.GoingStatus;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AttendanceRepository;
import com.psytrance.psytrance_tracker_backend.repository.AttendanceRepository.EventCount;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** "I'm going" marks: who goes where, and how many people go to each event. */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             UserRepository userRepository,
                             EventService eventService,
                             Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.clock = clock;
    }

    /**
     * The status of every requested event, in two queries however many events there are: one counts,
     * one checks the user's own marks. {@code username} is null for guests, who are never "going".
     */
    public Map<Long, GoingStatus> statusOf(Collection<Long> eventIds, String username) {
        Map<Long, GoingStatus> statuses = new LinkedHashMap<>();
        if (eventIds.isEmpty()) {
            return statuses;
        }
        Map<Long, Long> counts = attendanceRepository.countByEventIds(eventIds).stream()
                .collect(Collectors.toMap(EventCount::getEventId, EventCount::getTotal));
        Set<Long> mine = username == null ? Set.of() : userRepository.findByUsername(username)
                .map(user -> (Set<Long>) new HashSet<>(attendanceRepository.findEventIdsOfUser(user.getId(), eventIds)))
                .orElse(Set.of());
        for (Long eventId : eventIds) {
            statuses.put(eventId, new GoingStatus(counts.getOrDefault(eventId, 0L), mine.contains(eventId)));
        }
        return statuses;
    }

    public GoingStatus statusOf(long eventId, String username) {
        return statusOf(List.of(eventId), username).get(eventId);
    }

    /** Pressing "I'm going" again is fine and changes nothing. Unknown events are a 404. */
    public GoingStatus markGoing(String username, long eventId) {
        User user = findUser(username);
        // Also checks that the event exists, and gives the name and date the profile shows
        EventDto event = eventService.findById(eventId);
        attendanceRepository.markGoing(user.getId(), eventId, event.nameParty(), event.dateStart(), clock.instant());
        return statusOf(eventId, username);
    }

    /** Not going (any more). Fine to call when the user wasn't going in the first place. */
    public GoingStatus unmarkGoing(String username, long eventId) {
        User user = findUser(username);
        attendanceRepository.unmarkGoing(user.getId(), eventId);
        return statusOf(eventId, username);
    }

    /** Every event the user marked, soonest first. */
    public List<GoingEvent> eventsOf(String username) {
        return attendanceRepository.findByUserIdOrderByStartsOnAsc(findUser(username).getId()).stream()
                .map(GoingEvent::from)
                .toList();
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
