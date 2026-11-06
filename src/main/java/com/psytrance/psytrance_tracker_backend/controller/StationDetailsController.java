package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.map.StationDetails;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.psytrance.psytrance_tracker_backend.util.Logins.usernameOrNull;

/**
 * A label's or festival's map details (name, country, first year, description, genres): public for the map,
 * changed by editors, admins and the page's trusted owner.
 */
@RestController
public class StationDetailsController {

    private final StationDetails stationDetails;

    public StationDetailsController(StationDetails stationDetails) {
        this.stationDetails = stationDetails;
    }

    /** Every changed label and festival; the map puts them over its files */
    @GetMapping("/api/map/details")
    public List<StationDetails.Station> all() {
        return stationDetails.all();
    }

    /** Example: {@code GET /api/map/details/FESTIVAL?key=jungle}: the changed details and whether you may change them */
    @GetMapping("/api/map/details/{kind}")
    public StationDetails.Editable get(@PathVariable StationDetails.Kind kind, @RequestParam String key,
                                       Authentication authentication) {
        return stationDetails.editable(kind, key, usernameOrNull(authentication));
    }

    public record DetailsIn(@Size(max = 80) String name, @Size(max = 100) String country, Integer since,
                            @Size(max = 1000) String description, @Size(max = 6) List<String> genres) {
    }

    /** Example: {@code PUT /api/map/details/FESTIVAL?key=jungle {"name": "Jungle Festival", "genres": ["forest"]}} */
    @PutMapping("/api/map/details/{kind}")
    public StationDetails.Details save(@PathVariable StationDetails.Kind kind, @RequestParam String key,
                                       @jakarta.validation.Valid @RequestBody DetailsIn in, Authentication authentication) {
        return stationDetails.save(kind, key, new StationDetails.Details(in.name(), in.country(), in.since(),
                in.description(), in.genres()), usernameOrNull(authentication));
    }
}
