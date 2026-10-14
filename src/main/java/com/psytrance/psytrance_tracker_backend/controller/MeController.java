package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.GoingEvent;
import com.psytrance.psytrance_tracker_backend.dto.ReminderSettings;
import com.psytrance.psytrance_tracker_backend.service.AttendanceService;
import com.psytrance.psytrance_tracker_backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The logged-in user's own things. Everything here needs login. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AttendanceService attendanceService;
    private final UserService userService;

    public MeController(AttendanceService attendanceService, UserService userService) {
        this.attendanceService = attendanceService;
        this.userService = userService;
    }

    /** Events the user marked "I'm going", soonest first. */
    @GetMapping("/going")
    public List<GoingEvent> getMyEvents(Authentication authentication) {
        return attendanceService.eventsOf(authentication.getName());
    }

    /** Turns reminder emails on or off. */
    @PutMapping("/reminders")
    public ReminderSettings setReminders(@Valid @RequestBody ReminderSettings settings, Authentication authentication) {
        boolean enabled = userService.setEventReminders(authentication.getName(), settings.enabled());
        return new ReminderSettings(enabled);
    }
}
