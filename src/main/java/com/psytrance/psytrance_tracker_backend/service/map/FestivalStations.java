package com.psytrance.psytrance_tracker_backend.service.map;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.pages.FestivalStats;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which festival station of the map an event is an edition of ("Ozora Festival 2027" -> ozora), the way the
 * festival page finds its editions: the festival's name as a whole word in the event's name, plus the events set as
 * its editions by hand and minus the ones set as not (festival_editions). And whether an event counts as a festival
 * on the map: Goabase's or our form's kind "Festival", lasting three days or more (Friday to Sunday counts as three).
 */
@Component
public class FestivalStations {

    public static final int FESTIVAL_DAYS = 3;

    private final JdbcClient jdbc;
    private final MapFestivals mapFestivals;

    public FestivalStations(JdbcClient jdbc, MapFestivals mapFestivals) {
        this.jdbc = jdbc;
        this.mapFestivals = mapFestivals;
    }

    /** A festival on the map: kind Festival and three days or more */
    public static boolean isFestival(EventDto event) {
        if (event.nameType() == null || !"festival".equalsIgnoreCase(event.nameType().strip()) || event.dateStart() == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(event.dateStart(), event.lastDay()) + 1 >= FESTIVAL_DAYS;
    }

    /** {@code eventId -> festival id} for the events that are an edition of a festival on the map */
    public Map<Long, String> stationsOf(List<EventDto> events) {
        Map<String, Pattern> byName = new HashMap<>();
        mapFestivals.all().forEach((id, name) -> {
            String term = FestivalStats.matchOf(name);
            if (term.length() >= 3) {
                byName.put(id, Pattern.compile("(?iu)(^|[^\\p{L}\\p{N}])" + Pattern.quote(term.toLowerCase(Locale.ROOT)) + "($|[^\\p{L}\\p{N}])"));
            }
        });
        // Set by hand: event -> (festival -> is it an edition)
        Map<Long, Map<String, Boolean>> byHand = new HashMap<>();
        jdbc.sql("select festival_id, event_id, edition from festival_editions")
                .query((rs, row) -> {
                    byHand.computeIfAbsent(rs.getLong(2), k -> new HashMap<>()).put(rs.getString(1), rs.getBoolean(3));
                    return null;
                }).list();
        Map<Long, String> stations = new HashMap<>();
        for (EventDto event : events) {
            Map<String, Boolean> hand = byHand.getOrDefault(event.id(), Map.of());
            String found = hand.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
                    .filter(id -> mapFestivals.name(id).isPresent()).findFirst().orElse(null);
            if (found == null && event.nameParty() != null) {
                found = byName.entrySet().stream()
                        .filter(e -> !Boolean.FALSE.equals(hand.get(e.getKey())) && e.getValue().matcher(event.nameParty()).find())
                        .map(Map.Entry::getKey).sorted().findFirst().orElse(null);
            }
            if (found != null) {
                stations.put(event.id(), found);
            }
        }
        return stations;
    }
}
