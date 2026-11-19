package com.psytrance.psytrance_tracker_backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A crew as its members see it: who is aboard, the packing list and the sets nobody should miss. */
public record CrewView(String code, String name, long eventId, String eventName, String eventType,
                       LocalDate startsOn, LocalDate endsOn, String campSpot, boolean captain,
                       List<Member> members, List<Item> items, List<MustSee> sets) {

    public record Member(String username, boolean captain, boolean me) {
    }

    /** {@code bringer} is null until someone says "I'll bring it"; {@code canDelete}: you added it, bring it or are captain */
    public record Item(long id, String name, String bringer, boolean mine, boolean canDelete) {
    }

    /**
     * A set of the timetable the crew wants to see: someone follows the artist ({@code followedBy}) and/or added it
     * ({@code addedBy}). {@code clashesWith}: must-see sets on another stage at the same time.
     */
    public record MustSee(String artist, String stage, LocalDateTime startsAt, LocalDateTime endsAt,
                          List<String> followedBy, String addedBy, List<String> clashesWith) {
    }

    /** What the link shows before joining, and the lines of "Your crews" */
    public record Summary(String code, String name, long eventId, String eventName, LocalDate startsOn,
                          int members, boolean member) {
    }
}
