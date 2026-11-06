package com.psytrance.psytrance_tracker_backend.service.map;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.service.pages.PageTrust;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the map says about a label or festival station (its name, country, first year, description and genres),
 * changed by editors and admins, or by the page's trusted owner, without a deploy. Only changed stations have a row
 * (map_station_details); every change is logged in admin_changes with the details before and after, so it can be
 * undone from Admin -> History. A label's name can't change (its page, followers and crew hang on it); a festival's
 * can, and its former name still finds it (MapFestivals).
 */
@Service
public class StationDetails {

    public enum Kind { LABEL, FESTIVAL }

    /** null fields: as the map file has them */
    public record Details(String name, String country, Integer since, String description, List<String> genres) {
    }

    public record Station(Kind kind, String key, Details details) {
    }

    /** For an edit page: the changed details (all null when none) and whether this account may change them */
    public record Editable(Details details, boolean canEdit) {
    }

    private static final int MAX_GENRES = 6;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final MapGenres mapGenres;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final PageTrust pageTrust;
    private final Admins admins;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public StationDetails(JdbcClient jdbc, JsonMapper jsonMapper, MapGenres mapGenres, MapLabels mapLabels,
                          MapFestivals mapFestivals, PageTrust pageTrust, Admins admins,
                          AdminChangeRepository changeRepository, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.mapGenres = mapGenres;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.pageTrust = pageTrust;
        this.admins = admins;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    // Festival names someone changed, for the pages and the line-up matching
    @EventListener(ApplicationReadyEvent.class)
    public void useRenames() {
        mapFestivals.useRenames(all().stream()
                .filter(s -> s.kind() == Kind.FESTIVAL && s.details().name() != null)
                .collect(Collectors.toMap(Station::key, s -> s.details().name())));
    }

    /** Every changed station, for the map */
    public List<Station> all() {
        return jdbc.sql("select * from map_station_details order by kind, key")
                .query((rs, n) -> new Station(Kind.valueOf(rs.getString("kind")), rs.getString("key"),
                        new Details(rs.getString("name"), rs.getString("country"), (Integer) rs.getObject("since"),
                                rs.getString("description"), genres(rs.getString("genres")))))
                .list();
    }

    public Optional<Details> find(Kind kind, String key) {
        return all().stream().filter(s -> s.kind() == kind && s.key().equals(key)).map(Station::details).findFirst();
    }

    public Editable editable(Kind kind, String key, String username) {
        requireStation(kind, key);
        return new Editable(find(kind, key).orElse(new Details(null, null, null, null, null)), canEdit(kind, key, username));
    }

    /** Editors and admins, and the page's owner once an admin trusts them */
    public boolean canEdit(Kind kind, String key, String username) {
        return username != null && (admins.isEditor(username)
                || pageTrust.trusts(kind == Kind.LABEL ? PageTrust.Kind.LABEL : PageTrust.Kind.FESTIVAL, key, username));
    }

    @Transactional
    public Details save(Kind kind, String key, Details details, String username) {
        requireStation(kind, key);
        if (!canEdit(kind, key, username)) {
            throw new ResponseStatusException(username == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "Editors, admins and the page's trusted owner can change this. Ask an admin to trust your page.");
        }
        return write(kind, key, clean(kind, key, details), username, null);
    }

    /** Puts the details from before a change back, unless the station was changed again since */
    @Transactional
    public void undo(AdminChange change, String username) {
        String[] target = change.getTarget().split(":", 2);
        Kind kind = Kind.valueOf(target[0]);
        String now = json(find(kind, target[1]).orElse(null));
        if (!now.equals(String.valueOf(change.getAfterValue()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    target[1] + " was changed again since. Undo the newer change first.");
        }
        Details before = change.getBeforeValue() == null || "null".equals(change.getBeforeValue())
                ? null : jsonMapper.readValue(change.getBeforeValue(), Details.class);
        write(kind, target[1], before, username, change.getId());
    }

    private Details write(Kind kind, String key, Details details, String username, Long undoOf) {
        Details before = find(kind, key).orElse(null);
        jdbc.sql("delete from map_station_details where kind = ? and key = ?").params(kind.name(), key).update();
        boolean empty = details == null || (details.name() == null && details.country() == null && details.since() == null
                && details.description() == null && details.genres() == null);
        if (!empty) {
            jdbc.sql("""
                            insert into map_station_details (kind, key, name, country, since, description, genres, updated_by, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(kind.name(), key, details.name(), details.country(), details.since(), details.description(),
                            details.genres() == null ? null : String.join(",", details.genres()), username,
                            Timestamp.from(clock.instant()))
                    .update();
        }
        Details after = empty ? null : details;
        if (!json(before).equals(json(after))) {
            changeRepository.save(new AdminChange(username, AdminChange.STATION_DETAILS, kind.name() + ":" + key,
                    json(before), json(after), clock.instant(), undoOf));
        }
        useRenames();
        return after == null ? new Details(null, null, null, null, null) : after;
    }

    // Blank means "as the map file has it"; genres must be the map's, at most six
    private Details clean(Kind kind, String key, Details d) {
        String name = kind == Kind.FESTIVAL ? blank(d.name()) : null;
        if (name != null && name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name is too long");
        }
        if (name != null) {
            // Another festival with this name would make the line-up matching ambiguous
            mapFestivals.idOf(name).filter(id -> !id.equals(key)).ifPresent(id -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Another festival is called " + name);
            });
        }
        Integer since = d.since();
        if (since != null && (since < 1950 || since > 2100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The first year should be between 1950 and 2100");
        }
        List<String> genres = d.genres() == null ? null : List.copyOf(new LinkedHashSet<>(d.genres()));
        if (genres != null) {
            if (genres.size() > MAX_GENRES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_GENRES + " genres");
            }
            for (String g : genres) {
                if (!mapGenres.isGenre(g)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown genre: " + g);
                }
            }
            if (genres.isEmpty()) {
                genres = null;
            }
        }
        String description = blank(d.description());
        if (description != null && description.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The description is at most 1000 characters");
        }
        return new Details(name, blank(d.country()), since, description, genres);
    }

    private void requireStation(Kind kind, String key) {
        boolean exists = kind == Kind.LABEL ? mapLabels.isLabel(key) : mapFestivals.name(key).isPresent();
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such " + kind.name().toLowerCase() + ": " + key);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static List<String> genres(String value) {
        return value == null || value.isBlank() ? null : Arrays.stream(value.split(",")).map(String::strip).toList();
    }

    private String json(Details details) {
        return details == null ? "null" : jsonMapper.writeValueAsString(details);
    }
}
