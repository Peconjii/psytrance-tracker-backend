package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.OurEventDto;
import com.psytrance.psytrance_tracker_backend.dto.OurEventRequest;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.repository.OurEventRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

/**
 * Events added on our site, next to the ones from Goabase. Only accounts with a confirmed email may add one. It waits
 * for an admin, except when an admin adds it or the owner of the organizer's page (an admin linked them) adds an
 * event of theirs. Approved events are listed like Goabase parties (see {@link Parties}).
 */
@Service
public class OurEvents {

    /** Events one account can have waiting for an admin at once */
    static final int MAX_PENDING = 5;
    /** Longest event: a month (the longest festivals are about two weeks) */
    static final Duration MAX_LENGTH = Duration.ofDays(31);
    /** The kinds of event Goabase has, so our events sort the same way */
    public static final List<String> TYPES = List.of("Festival", "Open Air", "Club", "Indoor", "In- & Outdoor");

    private final OurEventRepository repository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final EventGenreAdmin eventGenreAdmin;
    private final MapGenres mapGenres;
    private final OrganizerEditing organizerEditing;
    private final Admins admins;
    private final AdminChangeRepository changeRepository;
    private final LineupScanRepository lineupScanRepository;
    private final DecisionMailer decisionMailer;
    private final Clock clock;

    public OurEvents(OurEventRepository repository, UserRepository userRepository, EventService eventService,
                     EventGenreAdmin eventGenreAdmin, MapGenres mapGenres, OrganizerEditing organizerEditing,
                     Admins admins, AdminChangeRepository changeRepository,
                     LineupScanRepository lineupScanRepository, DecisionMailer decisionMailer, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.eventGenreAdmin = eventGenreAdmin;
        this.mapGenres = mapGenres;
        this.organizerEditing = organizerEditing;
        this.admins = admins;
        this.changeRepository = changeRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.decisionMailer = decisionMailer;
        this.clock = clock;
    }

    @Transactional
    public OurEventDto submit(OurEventRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        boolean admin = admins.isEditor(username);
        // Admins and editors were picked by an admin, so they need no confirmed email
        if (!admin && !user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Confirm your email first: open the link we sent you");
        }
        String organizerName = blankToNull(request.organizerName());
        boolean trusted = admin
                || (organizerName != null && organizerEditing.canEdit(ArtistNames.slug(organizerName), username));
        if (!trusted && repository.countBySubmittedByIdAndStatus(user.getId(), OurEvent.PENDING) >= MAX_PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have " + MAX_PENDING + " events waiting for an admin already. Wait until they are checked.");
        }

        OurEvent event = new OurEvent(user, clock.instant());
        fill(event, request);
        repository.save(event);
        if (trusted) {
            approve(event, username);
        }
        return OurEventDto.of(event, null, List.of());
    }

    /** One event for its edit form: its sender or an admin */
    @Transactional(readOnly = true)
    public OurEventDto get(long id, String username) {
        return OurEventDto.of(editable(id, username), null, List.of());
    }

    /**
     * Changes an event, by its sender or an admin. A waiting one keeps waiting; an approved one changes on the map
     * right away (the sender has a confirmed email), and the change is in the history so admins can take it down.
     */
    @Transactional
    public OurEventDto update(long id, OurEventRequest request, String username) {
        OurEvent event = editable(id, username);
        if (OurEvent.REJECTED.equals(event.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A rejected event can't be changed; add it again");
        }
        List<String> genresBefore = event.genreIds();
        String nameBefore = event.getName();
        fill(event, request);
        repository.saveAndFlush(event);
        if (OurEvent.APPROVED.equals(event.getStatus())) {
            eventService.refreshSoon(event.eventId());
            if (!genresBefore.equals(event.genreIds())) {
                // None goes back to the genres the line-up gives
                eventGenreAdmin.setGenres(event.eventId(), event.genreIds(), username);
            }
            // The line-up and set times may have changed: read first in the next run
            lineupScanRepository.rereadSoon(List.of(event.eventId()));
            changeRepository.save(new AdminChange(username, AdminChange.OUR_EVENT_EDITED,
                    event.getName() + " (#" + event.eventId() + ")", nameBefore, event.getName(), clock.instant()));
        }
        return OurEventDto.of(event, null, List.of());
    }

    // The sender's own event, or any for an admin
    private OurEvent editable(long id, String username) {
        return repository.findById(id)
                .filter(event -> event.getSubmittedBy().getUsername().equals(username) || admins.isEditor(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No event " + id + " of yours"));
    }

    // Checks the request and copies it into the event
    private void fill(OurEvent event, OurEventRequest request) {
        event.setDetails(request.name().strip(), eventType(request.eventType()), request.startsAt(), request.endsAt(),
                timeZone(request), request.country().strip(), request.town().strip(), request.lat(), request.lon(),
                blankToNull(request.lineUp()), blankToNull(request.locationText()), blankToNull(request.entryFee()),
                blankToNull(request.info()), blankToNull(request.organizerName()), blankToNull(request.organizerUrl()),
                blankToNull(request.ticketUrl()), blankToNull(request.imageUrl()), genres(request.genres()));
    }

    /** The events this account added, newest first */
    @Transactional(readOnly = true)
    public List<OurEventDto> mine(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return repository.findBySubmittedByIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(event -> OurEventDto.of(event, null, List.of()))
                .toList();
    }

    /** Takes back an event that is still waiting; an approved one stays (ask an admin) */
    @Transactional
    public void withdraw(long id, String username) {
        OurEvent event = repository.findById(id)
                .filter(found -> found.getSubmittedBy().getUsername().equals(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No event " + id + " of yours"));
        if (!OurEvent.PENDING.equals(event.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an event that is still waiting can be taken back");
        }
        repository.delete(event);
    }

    /** For admins: events with this status (PENDING, APPROVED, REJECTED), soonest first, with possible duplicates */
    @Transactional(readOnly = true)
    public List<OurEventDto> list(String status) {
        String wanted = status == null ? OurEvent.PENDING : status.toUpperCase(Locale.ROOT);
        if (!Set.of(OurEvent.PENDING, OurEvent.APPROVED, OurEvent.REJECTED).contains(wanted)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
        List<EventDto> goabase = OurEvent.PENDING.equals(wanted)
                ? eventService.findUpcoming().stream().filter(e -> !OurEvent.isOurs(e.id())).toList()
                : List.of();
        return repository.findByStatusWithSubmitter(wanted).stream()
                .map(event -> OurEventDto.of(event, event.getSubmittedBy().getUsername(), duplicates(event, goabase)))
                .toList();
    }

    @Transactional
    public OurEventDto approve(long id, String adminUsername) {
        OurEvent event = find(id);
        if (!OurEvent.APPROVED.equals(event.getStatus())) {
            approve(event, adminUsername);
            tell(event, adminUsername, true, "It is on the map now; its line-up is read within half an hour.", null);
        }
        return OurEventDto.of(event, event.getSubmittedBy().getUsername(), List.of());
    }

    /** Rejects a waiting event, or takes down an approved one (it leaves the lists and the map) */
    @Transactional
    public OurEventDto reject(long id, String reason, String adminUsername) {
        OurEvent event = find(id);
        String before = event.getStatus();
        event.reject(adminUsername, blankToNull(reason), clock.instant());
        if (OurEvent.APPROVED.equals(before)) {
            eventService.refreshSoon(event.eventId());
        }
        log(event, before, adminUsername);
        if (!OurEvent.REJECTED.equals(before)) {
            tell(event, adminUsername, false, null, event.getRejectReason());
        }
        return OurEventDto.of(event, event.getSubmittedBy().getUsername(), List.of());
    }

    // A mail to whoever sent the event, unless the admin decided on their own event
    private void tell(OurEvent event, String adminUsername, boolean approved, String approvedNote, String reason) {
        User sender = event.getSubmittedBy();
        if (sender.getUsername().equals(adminUsername)) {
            return;
        }
        decisionMailer.send(sender.getEmail(), "Your event " + event.getName(), approved, approvedNote,
                approved ? "/events/" + event.eventId() : null, reason);
    }

    private void approve(OurEvent event, String username) {
        String before = event.getStatus();
        event.approve(username, clock.instant());
        repository.saveAndFlush(event);
        eventService.refreshSoon(event.eventId());
        // The genres the organizer picked, as hand-set genres: the line-up index leaves them alone
        if (!event.genreIds().isEmpty()) {
            eventGenreAdmin.setGenres(event.eventId(), event.genreIds(), username);
        }
        // First in line for the artist index, which reads the line-up and the set times
        lineupScanRepository.rereadSoon(List.of(event.eventId()));
        log(event, before, username);
    }

    private void log(OurEvent event, String before, String username) {
        changeRepository.save(new AdminChange(username, AdminChange.OUR_EVENT,
                event.getName() + " (#" + event.eventId() + ")", before, event.getStatus(), clock.instant()));
    }

    private OurEvent find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No added event " + id));
    }

    // Goabase events on about the same days (a day either side) in the same town, or in the same country with a word
    // of the name in common
    private static List<OurEventDto.Duplicate> duplicates(OurEvent event, List<EventDto> goabase) {
        LocalDate first = event.getStartsAt().toLocalDate().minusDays(1);
        LocalDate last = (event.getEndsAt() != null ? event.getEndsAt() : event.getStartsAt()).toLocalDate().plusDays(1);
        Set<String> words = words(event.getName());
        return goabase.stream()
                .filter(other -> other.dateStart() != null && !other.dateStart().isAfter(last)
                        && !other.lastDay().isBefore(first))
                .filter(other -> event.getTown().equalsIgnoreCase(other.nameTown())
                        || (event.getCountry().equalsIgnoreCase(other.nameCountry())
                            && words(other.nameParty()).stream().anyMatch(words::contains)))
                .limit(5)
                .map(other -> new OurEventDto.Duplicate(other.id(), other.nameParty(), other.dateStart(), other.nameTown()))
                .toList();
    }

    // The words of a name that say something: four letters or more, so "the", "of" and "2026" don't count
    private static Set<String> words(String name) {
        return name == null ? Set.of() : Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
                .filter(word -> word.length() >= 4)
                .collect(Collectors.toSet());
    }

    private String eventType(String type) {
        return TYPES.stream().filter(known -> known.equalsIgnoreCase(type.strip())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The kind of event is one of " + String.join(", ", TYPES)));
    }

    // Checks the time zone and the times: the event must not be over yet, end after it starts and last a month at most
    private String timeZone(OurEventRequest request) {
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timeZone().strip());
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone: " + request.timeZone());
        }
        LocalDateTime end = request.endsAt() != null ? request.endsAt() : request.startsAt();
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The event must end after it starts");
        }
        if (Duration.between(request.startsAt(), end).compareTo(MAX_LENGTH) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An event can last a month at most");
        }
        if (end.atZone(zone).toInstant().isBefore(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This event is already over");
        }
        return zone.getId();
    }

    private List<String> genres(List<String> genreIds) {
        List<String> wanted = genreIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(genreIds));
        for (String genreId : wanted) {
            if (!mapGenres.isGenre(genreId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown genre: " + genreId);
            }
        }
        return wanted;
    }
}
