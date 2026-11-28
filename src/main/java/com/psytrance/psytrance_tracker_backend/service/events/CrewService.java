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
import com.psytrance.psytrance_tracker_backend.service.account.PushNotifications;
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
    private final PushNotifications pushNotifications;

    public CrewService(JdbcClient jdbc, UserRepository userRepository, EventService eventService,
                       AttendanceService attendanceService, TimetableSlotRepository timetableSlotRepository, Clock clock,
                       PushNotifications pushNotifications) {
        this.pushNotifications = pushNotifications;
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
        return view(crew(code), me);
    }

    /** What the link shows: enough to decide whether to join */
    @Transactional(readOnly = true)
    public CrewView.Summary preview(String code, String username) {
        Crew crew = crew(code);
        long me = user(username).getId();
        return new CrewView.Summary(crew.code(), crew.name(), crew.eventId(), crew.eventName(), crew.startsOn(),
                memberCount(crew.id()), isMember(crew.id(), me));
    }

    @Transactional
    public CrewView join(String code, String username) {
        Crew crew = crew(code);
        User me = user(username);
        if (isMember(crew.id(), me.getId())) {
            return view(crew, me);
        }
        if (crew.endsOn().isBefore(today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This event is over");
        }
        if (wasRemoved(crew.id(), me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The captain removed you from this crew");
        }
        // Locks the crew row, so two people joining at once can't both take the last place
        jdbc.sql("select id from crew where id = :id for update").param("id", crew.id()).query(Long.class).single();
        if (memberCount(crew.id()) >= MAX_MEMBERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This crew is full (" + MAX_MEMBERS + ")");
        }
        addMember(crew.id(), me.getId());
        attendanceService.markGoing(username, crew.eventId());
        pushNotifications.joinedCrew(crew.id(), me.getId(), me.getUsername(), crew.name(), crew.code());
        return view(crew, me);
    }

    /**
     * The captain removes someone (who then can't come back with the same link), or anyone leaves (their own name).
     * When the captain leaves, the crew is gone.
     */
    @Transactional
    public void remove(String code, String username, String who) {
        Aboard a = aboard(code, username);
        boolean self = a.me().getUsername().equalsIgnoreCase(who);
        if (!self && !a.captain()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the captain can remove people");
        }
        if (self && a.captain()) {
            jdbc.sql("delete from crew where id = :id").param("id", a.crew().id()).update();
            return;
        }
        long other = self ? a.me().getId() : userRepository.findByUsername(who).map(User::getId)
                .filter(id -> isMember(a.crew().id(), id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not in this crew"));
        jdbc.sql("delete from crew_member where crew_id = :c and user_id = :u").param("c", a.crew().id()).param("u", other).update();
        // What they would bring goes back to "nobody yet"
        jdbc.sql("update crew_item set bringer_id = null where crew_id = :c and bringer_id = :u")
                .param("c", a.crew().id()).param("u", other).update();
        if (!self) {
            jdbc.sql("insert into crew_removed (crew_id, user_id) values (:c, :u) on conflict do nothing")
                    .param("c", a.crew().id()).param("u", other).update();
        }
    }

    /** A new link; the old one stops working. Captain only. */
    @Transactional
    public CrewView newLink(String code, String username) {
        Aboard a = captainAboard(code, username);
        String fresh = newCode();
        jdbc.sql("update crew set code = :n where id = :id").param("n", fresh).param("id", a.crew().id()).update();
        return view(crew(fresh), a.me());
    }

    @Transactional
    public CrewView setCampSpot(String code, String username, String spot) {
        Aboard a = aboard(code, username);
        jdbc.sql("update crew set camp_spot = :s where id = :id").param("s", blankToNull(spot)).param("id", a.crew().id()).update();
        return view(crew(code), a.me());
    }

    // --- packing list ---

    @Transactional
    public CrewView addItem(String code, String username, String name) {
        Aboard a = aboard(code, username);
        String clean = name.strip();
        Integer count = jdbc.sql("select count(*) from crew_item where crew_id = :c").param("c", a.crew().id()).query(Integer.class).single();
        if (count >= MAX_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The packing list is full");
        }
        // The same thing twice (any case) stays one line
        jdbc.sql("""
                        insert into crew_item (crew_id, name, added_by, created_at)
                        select :c, :n, :u, :now where not exists
                            (select 1 from crew_item where crew_id = :c and lower(name) = lower(:n))""")
                .param("c", a.crew().id()).param("n", clean).param("u", a.me().getId())
                .param("now", Timestamp.from(clock.instant())).update();
        return view(a);
    }

    /** "I'll bring it", or take it back. Someone else's item can't be taken over, even by two clicks at once. */
    @Transactional
    public CrewView toggleBring(String code, String username, long itemId) {
        Aboard a = aboard(code, username);
        long me = a.me().getId();
        // One statement, so two people pressing at the same moment can't both get it
        int changed = jdbc.sql("""
                        update crew_item set bringer_id = case when bringer_id is null then :me end
                        where id = :i and crew_id = :c and (bringer_id is null or bringer_id = :me)""")
                .param("me", me).param("i", itemId).param("c", a.crew().id()).update();
        if (changed == 0) {
            boolean exists = jdbc.sql("select count(*) from crew_item where id = :i and crew_id = :c")
                    .param("i", itemId).param("c", a.crew().id()).query(Integer.class).single() > 0;
            throw exists ? new ResponseStatusException(HttpStatus.CONFLICT, "Someone already brings it")
                    : new ResponseStatusException(HttpStatus.NOT_FOUND, "No such item");
        }
        return view(a);
    }

    /** Whoever added it, whoever brings it, or the captain */
    @Transactional
    public CrewView deleteItem(String code, String username, long itemId) {
        Aboard a = aboard(code, username);
        int gone = jdbc.sql("""
                        delete from crew_item where id = :i and crew_id = :c
                        and (:captain or added_by = :me or bringer_id = :me)""")
                .param("i", itemId).param("c", a.crew().id()).param("captain", a.captain()).param("me", a.me().getId()).update();
        if (gone == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only who added it, who brings it or the captain can delete it");
        }
        return view(a);
    }

    // --- must-see sets ---

    @Transactional
    public CrewView pick(String code, String username, String artist, LocalDateTime startsAt) {
        Aboard a = aboard(code, username);
        boolean inTimetable = timetableSlotRepository.findByEventIdOrderByPositionAsc(a.crew().eventId()).stream()
                .anyMatch(s -> s.getArtistName().equals(artist) && s.getStartsAt().equals(startsAt));
        if (!inTimetable) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such set in the timetable");
        }
        jdbc.sql("""
                        insert into crew_pick (crew_id, artist_name, starts_at, added_by) values (:c, :a, :s, :u)
                        on conflict (crew_id, artist_name, starts_at) do nothing""")
                .param("c", a.crew().id()).param("a", artist).param("s", Timestamp.valueOf(startsAt))
                .param("u", a.me().getId()).update();
        return view(a);
    }

    @Transactional
    public CrewView unpick(String code, String username, String artist, LocalDateTime startsAt) {
        Aboard a = aboard(code, username);
        jdbc.sql("delete from crew_pick where crew_id = :c and artist_name = :a and starts_at = :s")
                .param("c", a.crew().id()).param("a", artist).param("s", Timestamp.valueOf(startsAt)).update();
        return view(a);
    }

    // --- reading ---

    @Transactional(readOnly = true)
    public CrewView view(String code, String username) {
        return view(aboard(code, username));
    }

    private CrewView view(Aboard a) {
        return view(a.crew(), a.me());
    }

    private CrewView view(Crew crew, User user) {
        long me = user.getId();
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

        boolean captain = crew.ownerId() == me;
        List<CrewView.Item> items = jdbc.sql("select id, name, bringer_id, added_by from crew_item where crew_id = :c order by created_at, id")
                .param("c", crew.id())
                .query((rs, n) -> {
                    Long bringer = (Long) rs.getObject(3);
                    Long addedBy = (Long) rs.getObject(4);
                    boolean mine = bringer != null && bringer == me;
                    return new CrewView.Item(rs.getLong(1), rs.getString(2), bringer == null ? null : names.get(bringer),
                            mine, captain || mine || (addedBy != null && addedBy == me));
                }).list();

        return new CrewView(crew.code(), crew.name(), crew.eventId(), crew.eventName(), crew.eventType(),
                crew.startsOn(), crew.endsOn(), crew.campSpot(), crew.ownerId() == me, members, items,
                mustSee(crew, names));
    }

    @Transactional(readOnly = true)
    public List<CrewView.Summary> mine(String username) {
        return summaries(user(username).getId(), null);
    }

    /** The crews of one event the user is in (for the event page) */
    @Transactional(readOnly = true)
    public List<CrewView.Summary> mineForEvent(String username, long eventId) {
        return summaries(user(username).getId(), eventId);
    }

    // One query for the list, member counts included
    private List<CrewView.Summary> summaries(long userId, Long eventId) {
        return jdbc.sql("""
                        select c.code, c.name, c.event_id, c.event_name, c.starts_on,
                               (select count(*) from crew_member x where x.crew_id = c.id) as members
                        from crew c join crew_member m on m.crew_id = c.id
                        where m.user_id = :u and (cast(:e as bigint) is null or c.event_id = :e)
                        order by c.starts_on, c.name""")
                .param("u", userId).param("e", eventId)
                .query((rs, n) -> new CrewView.Summary(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getDate(5).toLocalDate(), rs.getInt(6), true))
                .list();
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

    private Crew crew(String code) {
        return jdbc.sql("select * from crew where code = :c").param("c", code == null ? "" : code)
                .query(this::mapCrew).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This crew link doesn't work any more"));
    }

    /** The crew of the link and who is asking, who must be in it */
    private record Aboard(Crew crew, User me) {
        boolean captain() {
            return crew.ownerId() == me.getId();
        }
    }

    private Aboard aboard(String code, String username) {
        Crew crew = crew(code);
        User me = user(username);
        if (!isMember(crew.id(), me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join the crew first");
        }
        return new Aboard(crew, me);
    }

    private Aboard captainAboard(String code, String username) {
        Aboard a = aboard(code, username);
        if (!a.captain()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the captain can do this");
        }
        return a;
    }

    private boolean wasRemoved(long crewId, long userId) {
        return jdbc.sql("select count(*) from crew_removed where crew_id = :c and user_id = :u")
                .param("c", crewId).param("u", userId).query(Integer.class).single() > 0;
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
