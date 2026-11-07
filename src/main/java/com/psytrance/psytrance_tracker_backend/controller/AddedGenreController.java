package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.map.AddedGenres;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Genres (stars) admins add to the galaxy: the map reads them, admins add, change and remove them */
@RestController
public class AddedGenreController {

    private final AddedGenres addedGenres;
    private final Admins admins;

    public AddedGenreController(AddedGenres addedGenres, Admins admins) {
        this.addedGenres = addedGenres;
        this.admins = admins;
    }

    /** {@code GET /api/map/added-genres}: drawn next to the genres of the bundled map file */
    @GetMapping("/api/map/added-genres")
    public List<AddedGenres.Genre> all() {
        return addedGenres.all();
    }

    /** {@code POST /api/admin/map/genres {"name": "Hypnotic psy", "family": "dark", "year": 2015, "links": ["forest", "prog"], ...}} */
    @PostMapping("/api/admin/map/genres")
    @ResponseStatus(HttpStatus.CREATED)
    public AddedGenres.Genre add(@RequestBody AddedGenres.Form form, Authentication authentication) {
        return addedGenres.add(form, admins.requireAdmin(authentication));
    }

    @PutMapping("/api/admin/map/genres/{id}")
    public AddedGenres.Genre update(@PathVariable String id, @RequestBody AddedGenres.Form form, Authentication authentication) {
        return addedGenres.update(id, form, admins.requireAdmin(authentication));
    }

    @DeleteMapping("/api/admin/map/genres/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id, Authentication authentication) {
        addedGenres.remove(id, admins.requireAdmin(authentication));
    }
}
