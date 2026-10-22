package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import com.psytrance.psytrance_tracker_backend.repository.OurEventRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every party the site shows: Goabase's, and the approved events added on our site, in Goabase's shape. Everything
 * that used to ask Goabase asks this instead, so our events get the same lists, line-up index, ships and pages.
 */
@Component
public class Parties {

    private final GoabaseClient goabaseClient;
    private final OurEventRepository ourEventRepository;

    public Parties(GoabaseClient goabaseClient, OurEventRepository ourEventRepository) {
        this.goabaseClient = goabaseClient;
        this.ourEventRepository = ourEventRepository;
    }

    /** Goabase's list (throws when Goabase is down, as before) and our approved events */
    public List<GoabaseParty> fetchAllParties() {
        List<GoabaseParty> parties = new ArrayList<>(goabaseClient.fetchAllParties());
        ourEventRepository.findByStatus(OurEvent.APPROVED).forEach(event -> parties.add(event.toParty()));
        return parties;
    }

    public Optional<GoabasePartyDetails> fetchParty(long id) {
        if (OurEvent.isOurs(id)) {
            return ourEventRepository.findById(id - OurEvent.ID_BASE)
                    .filter(event -> OurEvent.APPROVED.equals(event.getStatus()))
                    .map(OurEvent::toDetails);
        }
        return goabaseClient.fetchParty(id);
    }

    /** Goabase's genre keywords; our events have their genres set when they are approved */
    public List<String> fetchKeywords(long id) {
        return OurEvent.isOurs(id) ? List.of() : goabaseClient.fetchKeywords(id);
    }
}
