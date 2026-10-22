package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.ArtistSummary;
import com.psytrance.psytrance_tracker_backend.dto.GoingEvent;
import com.psytrance.psytrance_tracker_backend.dto.OrganizerDto;
import com.psytrance.psytrance_tracker_backend.dto.ReminderSettings;
import com.psytrance.psytrance_tracker_backend.service.ArtistEditing;
import com.psytrance.psytrance_tracker_backend.service.AttendanceService;
import com.psytrance.psytrance_tracker_backend.service.FestivalPages;
import com.psytrance.psytrance_tracker_backend.service.LabelPages;
import com.psytrance.psytrance_tracker_backend.service.OrganizerEditing;
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
    private final ArtistEditing artistEditing;
    private final OrganizerEditing organizerEditing;
    private final LabelPages labelPages;
    private final FestivalPages festivalPages;

    public MeController(AttendanceService attendanceService, UserService userService, ArtistEditing artistEditing,
                        OrganizerEditing organizerEditing, LabelPages labelPages, FestivalPages festivalPages) {
        this.attendanceService = attendanceService;
        this.userService = userService;
        this.artistEditing = artistEditing;
        this.organizerEditing = organizerEditing;
        this.labelPages = labelPages;
        this.festivalPages = festivalPages;
    }

    /** The labels whose page this account owns (linked by an admin) */
    @GetMapping("/labels")
    public List<LabelPages.Owned> getMyLabels(Authentication authentication) {
        return labelPages.ownedBy(authentication.getName());
    }

    /** The festivals whose page this account owns (linked by an admin) */
    @GetMapping("/festivals")
    public List<FestivalPages.Owned> getMyFestivals(Authentication authentication) {
        return festivalPages.ownedBy(authentication.getName());
    }

    /** The organizers whose page this account owns (linked by an admin) */
    @GetMapping("/organizers")
    public List<OrganizerDto.Summary> getMyOrganizers(Authentication authentication) {
        return organizerEditing.ownedBy(authentication.getName());
    }

    /** The artists whose profile this account owns (linked by an admin) */
    @GetMapping("/artists")
    public List<ArtistSummary> getMyArtists(Authentication authentication) {
        return artistEditing.ownedBy(authentication.getName());
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
