package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.MapExtras;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admins add labels and festivals to the map, without a change to the map files or a deploy */
@RestController
@RequestMapping("/api/admin/map")
public class AdminMapController {

    private final MapExtras mapExtras;
    private final Admins admins;

    public AdminMapController(MapExtras mapExtras, Admins admins) {
        this.mapExtras = mapExtras;
        this.admins = admins;
    }

    /** genres: map genre ids, 1 to 6: they decide where it sits */
    public record NewLabel(@NotBlank @Size(max = 60) String name, @Size(max = 100) String country, Integer since,
                           List<String> genres) {
    }

    public record NewFestival(@NotBlank @Size(max = 60) String name, @Size(max = 100) String country, Integer since,
                              @Size(max = 1000) String description, List<String> genres) {
    }

    /** {@code POST /api/admin/map/labels {"name": "Mittai Music", "country": "India", "since": 2022, "genres": ["hitech"]}} */
    @PostMapping("/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public MapExtras.Label addLabel(@Valid @RequestBody NewLabel label, Authentication authentication) {
        return mapExtras.addLabel(label.name(), label.country(), label.since(), label.genres(), admins.requireAdmin(authentication));
    }

    /** {@code DELETE /api/admin/map/labels?name=…}: only a label added from the panel */
    @DeleteMapping("/labels")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLabel(@RequestParam String name, Authentication authentication) {
        mapExtras.removeLabel(name, admins.requireAdmin(authentication));
    }

    /** {@code DELETE /api/admin/map/festivals?id=…}: only a festival added from the panel */
    @DeleteMapping("/festivals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFestival(@RequestParam String id, Authentication authentication) {
        mapExtras.removeFestival(id, admins.requireAdmin(authentication));
    }

    @PostMapping("/festivals")
    @ResponseStatus(HttpStatus.CREATED)
    public MapExtras.Festival addFestival(@Valid @RequestBody NewFestival festival, Authentication authentication) {
        return mapExtras.addFestival(festival.name(), festival.country(), festival.since(), festival.description(),
                festival.genres(), admins.requireAdmin(authentication));
    }
}
