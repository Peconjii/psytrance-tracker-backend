package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.FollowDto;
import com.psytrance.psytrance_tracker_backend.dto.FollowedGig;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.ArtistAlias;
import com.psytrance.psytrance_tracker_backend.model.ArtistFollow;
import com.psytrance.psytrance_tracker_backend.model.FollowNotice;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.ArtistAliasRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistEventRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistFollowRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.repository.FollowNoticeRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Following artists: the user's gigs glow on the map, and an hourly job emails gigs that are new since the
 * user started following (see BackgroundJobs). Gigs come from the artist index, so a gig is "new" once its
 * line-up has been read. Every gig is emailed once per user, however many followed artists play it.
 */
@Service
public class ArtistFollows {

    /** Enough for anyone's favourites, and a limit on what one account can make the job do */
    static final int MAX_FOLLOWS = 200;

    private final ArtistFollowRepository followRepository;
    private final FollowNoticeRepository noticeRepository;
    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository aliasRepository;
    private final ArtistEventRepository artistEventRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final ReminderMailer mailer;
    private final Clock clock;

    public ArtistFollows(ArtistFollowRepository followRepository, FollowNoticeRepository noticeRepository,
                         ArtistRepository artistRepository, ArtistAliasRepository aliasRepository,
                         ArtistEventRepository artistEventRepository, UserRepository userRepository,
                         EventService eventService, ReminderMailer mailer, Clock clock) {
        this.followRepository = followRepository;
        this.noticeRepository = noticeRepository;
        this.artistRepository = artistRepository;
        this.aliasRepository = aliasRepository;
        this.artistEventRepository = artistEventRepository;
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** The artists the user follows, by name */
    public List<FollowDto> follows(String username) {
        return followRepository.findByUserIdOrderByArtistNameAsc(user(username).getId()).stream()
                .map(follow -> new FollowDto(follow.getArtistSlug(), follow.getArtistName()))
                .toList();
    }

    /**
     * Follows an artist from the line-ups. The gigs they already have count as known, so the first email only
     * comes with a gig that is new. Following twice changes nothing.
     */
    @Transactional
    public FollowDto follow(String username, String slug) {
        User user = user(username);
        Artist artist = artist(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No artist called " + slug));
        Optional<ArtistFollow> existing = followRepository.findByUserIdAndArtistSlug(user.getId(), artist.getSlug());
        if (existing.isPresent()) {
            return new FollowDto(artist.getSlug(), existing.get().getArtistName());
        }
        if (followRepository.countByUserId(user.getId()) >= MAX_FOLLOWS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You follow " + MAX_FOLLOWS + " artists already");
        }
        Instant now = clock.instant();
        followRepository.save(new ArtistFollow(user, artist.getSlug(), artist.getName(), now));

        Set<Long> known = noticeRepository.findByUserId(user.getId()).stream()
                .map(FollowNotice::getEventId)
                .collect(Collectors.toSet());
        noticeRepository.saveAll(artistEventRepository.findEventIdsByArtistId(artist.getId()).stream()
                .filter(known::add)
                .map(eventId -> new FollowNotice(user.getId(), eventId, now))
                .toList());
        return new FollowDto(artist.getSlug(), artist.getName());
    }

    @Transactional
    public void unfollow(String username, String slug) {
        User user = user(username);
        // The slug as followed, or the artist's slug now, if an admin merged that spelling since
        for (String candidate : new HashSet<>(List.of(ArtistNames.slug(slug), canonicalSlug(slug)))) {
            followRepository.findByUserIdAndArtistSlug(user.getId(), candidate).ifPresent(followRepository::delete);
        }
    }

    /** Upcoming events where the artists the user follows play, soonest first, with who plays */
    public List<FollowedGig> gigs(String username) {
        List<ArtistFollow> follows = followRepository.findByUserIdOrderByArtistNameAsc(user(username).getId());
        if (follows.isEmpty()) {
            return List.of();
        }
        List<EventDto> upcoming = eventService.findUpcoming();
        Map<Long, List<String>> artistsByEvent = artistsByEvent(follows, new HashMap<>());
        return upcoming.stream()
                .filter(event -> artistsByEvent.containsKey(event.id()))
                .map(event -> new FollowedGig(event.id(), artistsByEvent.get(event.id())))
                .toList();
    }

    /** Emails every follower the upcoming gigs they don't know about yet. Returns how many emails went out. */
    public int sendNewGigEmails() {
        List<ArtistFollow> follows = followRepository.findAllWithUser();
        if (follows.isEmpty()) {
            return 0;
        }
        Map<Long, EventDto> upcoming = eventService.findUpcoming().stream()
                .collect(Collectors.toMap(EventDto::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, Set<Long>> knownByUser = new HashMap<>();
        for (FollowNotice notice : noticeRepository.findByEventIdIn(upcoming.keySet())) {
            knownByUser.computeIfAbsent(notice.getUserId(), id -> new HashSet<>()).add(notice.getEventId());
        }

        // The event ids of each followed artist, read once however many users follow them
        Map<String, List<Long>> eventsBySlug = new HashMap<>();
        Map<Long, List<ArtistFollow>> followsByUser = follows.stream()
                .collect(Collectors.groupingBy(follow -> follow.getUser().getId()));
        Instant now = clock.instant();
        int emailsSent = 0;
        for (List<ArtistFollow> userFollows : followsByUser.values()) {
            User user = userFollows.get(0).getUser();
            // The same switch as the event reminders: one "emails from us" setting on the profile
            if (!user.isEventReminders() || user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            Set<Long> known = knownByUser.getOrDefault(user.getId(), Set.of());
            Map<Long, List<String>> artistsByEvent = artistsByEvent(userFollows, eventsBySlug);
            List<ReminderMailer.Gig> gigs = new ArrayList<>();
            artistsByEvent.forEach((eventId, artists) -> {
                if (upcoming.containsKey(eventId) && !known.contains(eventId)) {
                    gigs.add(new ReminderMailer.Gig(upcoming.get(eventId), artists));
                }
            });
            if (gigs.isEmpty()) {
                continue;
            }
            gigs.sort(Comparator.comparing(gig -> gig.event().dateStart(), Comparator.nullsLast(Comparator.naturalOrder())));
            // Recorded only after a successful send, so a failed email is tried again on the next run
            if (mailer.sendNewGigs(user, gigs)) {
                noticeRepository.saveAll(gigs.stream()
                        .map(gig -> new FollowNotice(user.getId(), gig.event().id(), now))
                        .toList());
                emailsSent++;
            }
        }
        return emailsSent;
    }

    // Event id -> names of the followed artists who play it
    private Map<Long, List<String>> artistsByEvent(List<ArtistFollow> follows, Map<String, List<Long>> eventsBySlug) {
        Map<Long, List<String>> artistsByEvent = new HashMap<>();
        for (ArtistFollow follow : follows) {
            List<Long> eventIds = eventsBySlug.computeIfAbsent(follow.getArtistSlug(), slug -> artist(slug)
                    .map(artist -> artistEventRepository.findEventIdsByArtistId(artist.getId()))
                    .orElse(List.of()));
            for (Long eventId : eventIds) {
                artistsByEvent.computeIfAbsent(eventId, id -> new ArrayList<>()).add(follow.getArtistName());
            }
        }
        return artistsByEvent;
    }

    // The artist with this slug, also when an admin merged that spelling into another artist since
    private Optional<Artist> artist(String slug) {
        String clean = ArtistNames.slug(slug);
        return artistRepository.findBySlug(clean)
                .or(() -> aliasRepository.findById(clean).map(ArtistAlias::getArtistId).flatMap(artistRepository::findById));
    }

    private String canonicalSlug(String slug) {
        return artist(slug).map(Artist::getSlug).orElse(ArtistNames.slug(slug));
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
