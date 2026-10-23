package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.GoingStatus;
import com.psytrance.psytrance_tracker_backend.service.AttendanceService;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.psytrance.psytrance_tracker_backend.util.Logins.usernameOrNull;

/** "I'm going" on events. Reading counts is public; marking needs login (see SecurityConfig). */
@RestController
@RequestMapping("/api/events")
public class GoingController {

    private final AttendanceService attendanceService;

    public GoingController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * The status of a whole page of events in one request, e.g. {@code GET /api/events/going?ids=1,2,3}
     * answers {@code {"1": {"count": 4, "going": true}, "2": {"count": 0, "going": false}, ...}}.
     */
    @GetMapping("/going")
    public Map<Long, GoingStatus> getGoingForEvents(@RequestParam @Size(max = 100) List<Long> ids,
                                                    Authentication authentication) {
        return attendanceService.statusOf(ids, usernameOrNull(authentication));
    }

    @GetMapping("/{id}/going")
    public GoingStatus getGoing(@PathVariable long id, Authentication authentication) {
        return attendanceService.statusOf(id, usernameOrNull(authentication));
    }

    @PutMapping("/{id}/going")
    public GoingStatus markGoing(@PathVariable long id, Authentication authentication) {
        return attendanceService.markGoing(authentication.getName(), id);
    }

    @DeleteMapping("/{id}/going")
    public GoingStatus unmarkGoing(@PathVariable long id, Authentication authentication) {
        return attendanceService.unmarkGoing(authentication.getName(), id);
    }
}
