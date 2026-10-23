package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.OrganizerDto;
import com.psytrance.psytrance_tracker_backend.dto.OrganizerEditDto;
import com.psytrance.psytrance_tracker_backend.dto.OrganizerProfileRequest;
import com.psytrance.psytrance_tracker_backend.dto.TimetableDto;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.EventSummary;
import com.psytrance.psytrance_tracker_backend.model.Organizer;
import com.psytrance.psytrance_tracker_backend.model.OrganizerOwner;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.EventSummaryRepository;
import com.psytrance.psytrance_tracker_backend.repository.OrganizerOwnerRepository;
import com.psytrance.psytrance_tracker_backend.repository.OrganizerRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Organizers editing their own page, like artists (see ArtistEditing): an admin links an account to the organizer;
 * the owner and admins write the page and the timetables of the organizer's own events. A timetable lights up the
 * event's live fleet on the map, so only events Goabase says this organizer runs are theirs to edit.
 */
@Service
public class OrganizerEditing {

    private final OrganizerRepository organizerRepository;
    private final OrganizerOwnerRepository ownerRepository;
    private final EventSummaryRepository summaryRepository;
    private final UserRepository userRepository;
    private final AdminChangeRepository changeRepository;
    private final EventService eventService;
    private final EventTimetables eventTimetables;
    private final Admins admins;
    private final Clock clock;
    private final PageTrust pageTrust;

    public OrganizerEditing(OrganizerRepository organizerRepository, OrganizerOwnerRepository ownerRepository,
                            EventSummaryRepository summaryRepository, UserRepository userRepository,
                            AdminChangeRepository changeRepository, EventService eventService,
                            EventTimetables eventTimetables, Admins admins, Clock clock, PageTrust pageTrust) {
        this.organizerRepository = organizerRepository;
        this.ownerRepository = ownerRepository;
        this.summaryRepository = summaryRepository;
        this.userRepository = userRepository;
        this.changeRepository = changeRepository;
        this.eventService = eventService;
        this.eventTimetables = eventTimetables;
        this.admins = admins;
        this.clock = clock;
        this.pageTrust = pageTrust;
    }

    /** The page, the organizer's upcoming events and (for admins) the owner */
    public OrganizerEditDto edit(String slug, String username) {
        Organizer organizer = requireEditor(slug, username);
        Set<Long> theirs = eventIdsOf(organizer.getSlug());
        List<OrganizerEditDto.Event> events = eventService.findUpcoming().stream()
                .filter(event -> theirs.contains(event.id()))
                .map(event -> new OrganizerEditDto.Event(event.id(), event.nameParty(), event.dateStart(), event.nameTown()))
                .toList();
        String owner = admins.isAdmin(username)
                ? ownerRepository.findById(organizer.getSlug()).map(o -> o.getUser().getUsername()).orElse(null)
                : null;
        return new OrganizerEditDto(organizer.getSlug(), organizer.getName(), organizer.getPhotoUrl(), organizer.getBio(),
                organizer.getInstagramUrl(), organizer.getWebsiteUrl(), events, owner);
    }

    @Transactional
    public OrganizerDto.Profile saveProfile(String slug, OrganizerProfileRequest request, String username) {
        Organizer organizer = requireEditor(slug, username);
        organizer.updateProfile(request.photoUrl(), request.bio(), request.instagramUrl(), request.websiteUrl());
        organizerRepository.save(organizer);
        log(username, AdminChange.ORGANIZER_PROFILE, organizer.getName(), null, "page written");
        return OrganizerDto.Profile.of(organizer);
    }

    /** A timetable for one of the organizer's own events, as an admin would write it (see EventTimetables) */
    public TimetableDto setTimetable(String slug, long eventId, List<TimetableDto.Set> sets, String username) {
        requireOwnEvent(slug, eventId, username);
        return eventTimetables.setManual(eventId, sets, username);
    }

    public TimetableDto handBackTimetable(String slug, long eventId, String username) {
        requireOwnEvent(slug, eventId, username);
        return eventTimetables.handBack(eventId, username);
    }

    @Transactional
    public String setOwner(String slug, String ownerUsername, String adminUsername) {
        Organizer organizer = organizer(slug);
        User user = userRepository.findByUsername(ownerUsername == null ? "" : ownerUsername.strip())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account called " + ownerUsername));
        String before = ownerRepository.findById(organizer.getSlug()).map(o -> o.getUser().getUsername()).orElse(null);
        ownerRepository.save(new OrganizerOwner(organizer.getSlug(), user, clock.instant()));
        if (!user.getUsername().equals(before)) {
            pageTrust.reset(PageTrust.Kind.ORGANIZER, organizer.getSlug());
        }
        log(adminUsername, AdminChange.ORGANIZER_OWNER, organizer.getName(), before, user.getUsername());
        return user.getUsername();
    }

    @Transactional
    public void removeOwner(String slug, String adminUsername) {
        Organizer organizer = organizer(slug);
        ownerRepository.findById(organizer.getSlug()).ifPresent(owner -> {
            ownerRepository.delete(owner);
            log(adminUsername, AdminChange.ORGANIZER_OWNER, organizer.getName(), owner.getUser().getUsername(), null);
        });
    }

    /** The organizers whose page this account owns */
    public List<OrganizerDto.Summary> ownedBy(String username) {
        return userRepository.findByUsername(username)
                .map(user -> organizerRepository.findAllById(ownerRepository.findByUserId(user.getId()).stream()
                                .map(OrganizerOwner::getOrganizerSlug).toList()).stream()
                        .map(organizer -> new OrganizerDto.Summary(organizer.getSlug(), organizer.getName(), 0))
                        .toList())
                .orElse(List.of());
    }

    public boolean canEdit(String slug, String username) {
        if (username == null) {
            return false;
        }
        return admins.isAdmin(username) || ownerRepository.findById(ArtistNames.slug(slug))
                .map(owner -> owner.getUser().getUsername().equals(username))
                .orElse(false);
    }

    private void requireOwnEvent(String slug, long eventId, String username) {
        Organizer organizer = requireEditor(slug, username);
        if (!admins.isAdmin(username) && !eventIdsOf(organizer.getSlug()).contains(eventId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This event is not run by " + organizer.getName());
        }
    }

    private Set<Long> eventIdsOf(String organizerSlug) {
        return summaryRepository.findByOrganizerSlug(organizerSlug).stream()
                .map(EventSummary::getEventId)
                .collect(Collectors.toSet());
    }

    // The organizer, for an admin or the organizer's owner; guests get 401 and other users 403
    private Organizer requireEditor(String slug, String username) {
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first");
        }
        Organizer organizer = organizer(slug);
        if (!canEdit(organizer.getSlug(), username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the organizer and admins can edit this page");
        }
        return organizer;
    }

    private Organizer organizer(String slug) {
        return organizerRepository.findById(ArtistNames.slug(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer not found"));
    }

    private void log(String username, String kind, String target, String before, String after) {
        changeRepository.save(new AdminChange(username, kind, target, before, after, clock.instant()));
    }
}
