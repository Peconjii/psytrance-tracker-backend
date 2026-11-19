package com.psytrance.psytrance_tracker_backend.service.events;

import com.psytrance.psytrance_tracker_backend.dto.CrewView;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.model.TimetableSlot;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.TimetableSlotRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Crews: a private group of friends for one event. You need an account and the crew's link (its code) to see it;
 * joining marks you as going. A crew has a packing list ("I'll bring it") and must-see sets: sets of artists someone
 * in the crew follows, plus sets added by hand, with clashes between stages. Crews go 7 days after the event ends.
 */
@Service
public class CrewService {

    private static final Logger log = LoggerFactory.getLogger(CrewService.class);

    public static final int MAX_MEMBERS = 20;
    static final int MAX_ITEMS = 80;
    static final int MAX_OWN_CREWS_PER_EVENT = 3;
    static final int KEEP_DAYS_AFTER_EVENT = 7;
    /** Sets without an end time count as this long when looking for clashes */
    static final int DEFAULT_SET_MINUTES = 90;

    private static final String CODE_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final AttendanceService attendanceService;
    private final TimetableSlotRepository timetableSlotRepository;
    private final Clock clock;

    public CrewService(JdbcClient jdbc, UserRepository userRepository, EventService eventService,
                       AttendanceService attendanceService, TimetableSlotRepository timetableSlotRepository, Clock clock) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.timetableSlotRepository = timetableSlotRepository;
        this.clock = clock;
    }

    private record Crew(long id, long eventId, String eventName, String eventType, LocalDate startsOn,
                        LocalDate endsOn, String name, String code, long ownerId, String campSpot) {
    }

    // --- creating and joining ---

    @Transactional
    public CrewView create(String username, long eventId, String name) {
        User me = user(username);
        EventDto event = eventService.findById(eventId);
        LocalDate starts = event.dateStart();
        LocalDate ends = event.lastDay() != null ? event.lastDay() : starts;
        if (starts == null || ends.isBefore(today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This event is over");
        }
        Integer own = jdbc.sql("select count(*) from crew where owner_id = :u and event_id = :e")
                .param("u", me.getId()).param("e", eventId).query(Integer.class).single();
        if (own >= MAX_OWN_CREWS_PER_EVENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already have " + own + " crews for this event");
        }
        String code = newCode();
        Long crewId = jdbc.sql("""
                        insert into crew (event_id, event_name, event_type, starts_on, ends_on, name, code, owner_id, created_at)
                        values (:e, :en, :et, :s, :end, :n, :c, :o, :now) returning id""")
                .param("e", eventId).param("en", truncate(event.nameParty(), 300)).param("et", truncate(event.nameType(), 100))
                .param("s", starts).param("end", ends).param("n", name.strip()).param("c", code)
                .param("o", me.getId()).param("now", Timestamp.from(clock.instant()))
                .query(Long.class).single();
        addMember(crewId, me.getId());
        attendanceService.markGoing(username, eventId);
        return view(code, username);
    }

    /** What the link shows: enough to decide whether to join */
    @Transactional(readOnly = true)
    public CrewView.Summary preview(String code, String username) {
        Crew crew = crew(code);
        User me = user(username);
        return summary(crew, isMember(crew.id(), me.getId()));
    }

    @Transactional
    public CrewView join(String code, String username) {
        Crew crew = crew(code);
        User me = user(username);
        if (!isMember(crew.id(), me.getId())) {
            if (memberCount(crew.id()) >= MAX_MEMBERS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This crew is full (" + MAX_MEMBERS + ")");
            }
            addMember(crew.id(), me.getId());
            attendanceService.markGoing(username, crew.eventId());
        }
        return view(code, username);
    }

    /** The captain removes someone, or anyone leaves (their own name). When the captain leaves, the crew is gone. */
    @Transactional
    public void remove(String code, String username, String who) {
        Crew crew = crewOfMember(code, username);
        User me = user(username);
        boolean self = me.getUsername().equalsIgnoreCase(who);
        if (!self && me.getId() != crew.ownerId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the captain can remove people");
        }
        if (self && me.getId() == crew.ownerId()) {
            jdbc.sql("delete from crew where id = :id").param("id", crew.id()).update();
            return;
        }
        User other = userRepository.findByUsername(who)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not in this crew"));
        jdbc.sql("delete from crew_member where crew_id = :c and user_id = :u").param("c", crew.id()).param("u", other.getId()).update();
        // What they would bring goes back to "nobody yet"
        jdbc.sql("update crew_item set bringer_id = null where crew_id = :c and bringer_id = :u")
                .param("c", crew.id()).param("u", other.getId()).update();
    }

    /** A new link; the old one stops working. Captain only. */
    @Transactional
    public CrewView newLink(String code, String username) {
        Crew crew = captainCrew(code, username);
        String fresh = newCode();
        jdbc.sql("update crew set code = :n where id = :id").param("n", fresh).param("id", crew.id()).update();
        return view(fresh, username);
    }

    @Transactional
    public CrewView setCampSpot(String code, String username, String spot) {
        Crew crew = crewOfMember(code, username);
        jdbc.sql("update crew set camp_spot = :s where id = :id").param("s", blankToNull(spot)).param("id", crew.id()).update();
        return view(code, username);
    }

    // --- packing list ---

    @Transactional
    public CrewView addItem(String code, String username, String name) {
        Crew crew = crewOfMember(code, username);
        String clean = name.strip();
        Integer count = jdbc.sql("select count(*) from crew_item where crew_id = :c").param("c", crew.id()).query(Integer.class).single();
        if (count >= MAX_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The packing list is full");
        }
        Integer same = jdbc.sql("select count(*) from crew_item where crew_id = :c and lower(name) = lower(:n)")
                .param("c", crew.id()).param("n", clean).query(Integer.class).single();
        if (same == 0) {
            jdbc.sql("insert into crew_item (crew_id, name, added_by, created_at) values (:c, :n, :u, :now)")
                    .param("c", crew.id()).param("n", clean).param("u", user(username).getId())
                    .param("now", Timestamp.from(clock.instant())).update();
        }
        return view(code, username);
    }

    /** "I'll bring it", or take it back. Someone else's item can't be taken over. */
    @Transactional
    public CrewView toggleBring(String code, String username, long itemId) {
        Crew crew = crewOfMember(code, username);
        long me = user(username).getId();
        // A one-element list holding the bringer (null = nobody yet); empty when there is no such item
        List<Long> found = jdbc.sql("select bringer_id from crew_item where id = :i and crew_id = :c")
                .param("i", itemId).param("c", crew.id()).query((rs, n) -> (Long) rs.getObject(1)).list();
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such item");
        }
        Long current = found.get(0);
        if (current != null && current != me) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Someone already brings it");
        }
        jdbc.sql("update crew_item set bringer_id = :b where id = :i")
                .param("b", current == null ? me : null).param("i", itemId).update();
        return view(code, username);
    }

    @Transactional
    public CrewView deleteItem(String code, String username, long itemId) {
        Crew crew = crewOfMember(code, username);
        jdbc.sql("delete from crew_item where id = :i and crew_id = :c").param("i", itemId).param("c", crew.id()).update();
        return view(code, username);
    }

    // --- must-see sets ---

    @Transactional
    public CrewView pick(String code, String username, String artist, LocalDateTime startsAt) {
        Crew crew = crewOfMember(code, username);
        boolean inTimetable = timetableSlotRepository.findByEventIdOrderByPositionAsc(crew.eventId()).stream()
                .anyMatch(s -> s.getArtistName().equals(artist) && s.getStartsAt().equals(startsAt));
        if (!inTimetable) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such set in the timetable");
        }
        jdbc.sql("""
                        insert into crew_pick (crew_id, artist_name, starts_at, added_by) values (:c, :a, :s, :u)
                        on conflict (crew_id, artist_name, starts_at) do nothing""")
                .param("c", crew.id()).param("a", artist).param("s", Timestamp.valueOf(startsAt))
                .param("u", user(username).getId()).update();
        return view(code, username);
    }

    @Transactional
    public CrewView unpick(String code, String username, String artist, LocalDateTime startsAt) {
        Crew crew = crewOfMember(code, username);
        jdbc.sql("delete from crew_pick where crew_id = :c and artist_name = :a and starts_at = :s")
                .param("c", crew.id()).param("a", artist).param("s", Timestamp.valueOf(startsAt)).update();
        return view(code, username);
    }

    // --- reading ---

    @Transactional(readOnly = true)
    public CrewView view(String code, String username) {
        Crew crew = crewOfMember(code, username);
        long me = user(username).getId();

        record Row(long id, String username) {
        }
        List<Row> people = jdbc.sql("""
                        select u.id, u.username from crew_member m join app_user u on u.id = m.user_id
                        where m.crew_id = :c order by m.joined_at, u.username""")
                .param("c", crew.id()).query((rs, n) -> new Row(rs.getLong(1), rs.getString(2))).list();
        Map<Long, String> names = new HashMap<>();
        people.forEach(p -> names.put(p.id(), p.username()));
        List<CrewView.Member> members = people.stream()
                .sorted(Comparator.comparing((Row p) -> p.id() != crew.ownerId()))
                .map(p -> new CrewView.Member(p.username(), p.id() == crew.ownerId(), p.id() == me))
                .toList();

        List<CrewView.Item> items = jdbc.sql("select id, name, bringer_id from crew_item where crew_id = :c order by created_at, id")
                .param("c", crew.id())
                .query((rs, n) -> {
                    Long bringer = (Long) rs.getObject(3);
                    return new CrewView.Item(rs.getLong(1), rs.getString(2), bringer == null ? null : names.get(bringer),
                            bringer != null && bringer == me);
                }).list();

        return new CrewView(crew.code(), crew.name(), crew.eventId(), crew.eventName(), crew.eventType(),
                crew.startsOn(), crew.endsOn(), crew.campSpot(), crew.ownerId() == me, members, items,
                mustSee(crew, names));
    }

    @Transactional(readOnly = true)
    public List<CrewView.Summary> mine(String username) {
        long me = user(username).getId();
        return jdbc.sql("""
                        select c.* from crew c join crew_member m on m.crew_id = c.id
                        where m.user_id = :u order by c.starts_on, c.name""")
                .param("u", me).query(this::mapCrew).list().stream()
                .map(c -> summary(c, true)).toList();
    }

    /** The crews of one event the user is in (for the event page) */
    @Transactional(readOnly = true)
    public List<CrewView.Summary> mineForEvent(String username, long eventId) {
        return mine(username).stream().filter(s -> s.eventId() == eventId).toList();
    }

    /** Sets of artists someone in the crew follows, plus the ones added by hand, in time order, with clashes */
    private List<CrewView.MustSee> mustSee(Crew crew, Map<Long, String> names) {
        List<TimetableSlot> slots = timetableSlotRepository.findByEventIdOrderByPositionAsc(crew.eventId());
        if (slots.isEmpty()) {
            return List.of();
        }
        // slug of a followed artist → who in the crew follows them
        Map<String, List<String>> followers = new HashMap<>();
        jdbc.sql("select f.artist_slug, f.user_id from artist_follows f join crew_member m on m.user_id = f.user_id where m.crew_id = :c")
                .param("c", crew.id())
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getLong(2))).list()
                .forEach(e -> followers.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(names.get(e.getValue())));
        Map<String, String> picks = new HashMap<>();
        jdbc.sql("select artist_name, starts_at, added_by from crew_pick where crew_id = :c").param("c", crew.id())
                .query((rs, n) -> {
                    Long by = (Long) rs.getObject(3);
                    picks.put(rs.getString(1) + "|" + rs.getTimestamp(2).toLocalDateTime(), by == null ? "" : names.getOrDefault(by, ""));
                    return null;
                }).list();

        Map<TimetableSlot, CrewView.MustSee> chosen = new LinkedHashMap<>();
        for (TimetableSlot s : slots) {
            List<String> by = followers.getOrDefault(followSlug(s.getArtistName()), List.of()).stream()
                    .filter(Objects::nonNull).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            String addedBy = picks.get(s.getArtistName() + "|" + s.getStartsAt());
            if (!by.isEmpty() || addedBy != null) {
                chosen.put(s, new CrewView.MustSee(s.getArtistName(), s.getStage(), s.getStartsAt(), s.getEndsAt(),
                        by, addedBy, List.of()));
            }
        }
        List<CrewView.MustSee> result = new ArrayList<>();
        for (CrewView.MustSee m : chosen.values()) {
            List<String> clashes = chosen.values().stream()
                    .filter(o -> o != m && !Objects.equals(stage(o), stage(m)) && overlaps(m, o))
                    .map(CrewView.MustSee::artist).toList();
            result.add(new CrewView.MustSee(m.artist(), m.stage(), m.startsAt(), m.endsAt(), m.followedBy(), m.addedBy(), clashes));
        }
        result.sort(Comparator.comparing(CrewView.MustSee::startsAt));
        return result;
    }

    // "Astrix (live)" in a timetable is the followed "astrix"
    static String followSlug(String artist) {
        return ArtistNames.slug(artist.replaceAll("(?i)\\s*[(\\[]?\\s*(live|dj set)\\s*[)\\]]?\\s*$", ""));
    }

    private static String stage(CrewView.MustSee m) {
        return m.stage() == null ? "" : m.stage().strip().toLowerCase(Locale.ROOT);
    }

    static boolean overlaps(CrewView.MustSee a, CrewView.MustSee b) {
        return a.startsAt().isBefore(end(b)) && b.startsAt().isBefore(end(a));
    }

    private static LocalDateTime end(CrewView.MustSee m) {
        return m.endsAt() != null && m.endsAt().isAfter(m.startsAt()) ? m.endsAt() : m.startsAt().plusMinutes(DEFAULT_SET_MINUTES);
    }

    // --- cleanup ---

    /** Every night: crews whose event ended more than a week ago are deleted with everything in them */
    @Scheduled(cron = "${app.crews.cleanup-cron:0 30 4 * * *}")
    public void deleteOldCrewsJob() {
        try {
            int gone = deleteOldCrews();
            if (gone > 0) {
                log.info("Deleted {} crews of past events", gone);
            }
        } catch (RuntimeException e) {
            log.warn("Crew cleanup failed", e);
        }
    }

    @Transactional
    public int deleteOldCrews() {
        return jdbc.sql("delete from crew where ends_on < :limit")
                .param("limit", today().minusDays(KEEP_DAYS_AFTER_EVENT)).update();
    }

    // --- helpers ---

    private CrewView.Summary summary(Crew crew, boolean member) {
        return new CrewView.Summary(crew.code(), crew.name(), crew.eventId(), crew.eventName(), crew.startsOn(),
                memberCount(crew.id()), member);
    }

    private Crew crew(String code) {
        return jdbc.sql("select * from crew where code = :c").param("c", code == null ? "" : code)
                .query(this::mapCrew).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This crew link doesn't work any more"));
    }

    private Crew crewOfMember(String code, String username) {
        Crew crew = crew(code);
        if (!isMember(crew.id(), user(username).getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join the crew first");
        }
        return crew;
    }

    private Crew captainCrew(String code, String username) {
        Crew crew = crewOfMember(code, username);
        if (crew.ownerId() != user(username).getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the captain can do this");
        }
        return crew;
    }

    private Crew mapCrew(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Crew(rs.getLong("id"), rs.getLong("event_id"), rs.getString("event_name"), rs.getString("event_type"),
                rs.getDate("starts_on").toLocalDate(), rs.getDate("ends_on").toLocalDate(), rs.getString("name"),
                rs.getString("code"), rs.getLong("owner_id"), rs.getString("camp_spot"));
    }

    private boolean isMember(long crewId, long userId) {
        return jdbc.sql("select count(*) from crew_member where crew_id = :c and user_id = :u")
                .param("c", crewId).param("u", userId).query(Integer.class).single() > 0;
    }

    private int memberCount(long crewId) {
        return jdbc.sql("select count(*) from crew_member where crew_id = :c").param("c", crewId).query(Integer.class).single();
    }

    private void addMember(long crewId, long userId) {
        jdbc.sql("insert into crew_member (crew_id, user_id, joined_at) values (:c, :u, :now) on conflict do nothing")
                .param("c", crewId).param("u", userId).param("now", Timestamp.from(clock.instant())).update();
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CODE_LETTERS.charAt(RANDOM.nextInt(CODE_LETTERS.length())));
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
