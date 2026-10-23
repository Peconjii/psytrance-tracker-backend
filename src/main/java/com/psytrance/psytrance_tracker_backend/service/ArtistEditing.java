package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.ArtistChangeRequest;
import com.psytrance.psytrance_tracker_backend.dto.ArtistEditDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistListsRequest;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileRequest;
import com.psytrance.psytrance_tracker_backend.dto.ArtistRequestDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistSummary;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.ArtistFestival;
import com.psytrance.psytrance_tracker_backend.model.ArtistOwner;
import com.psytrance.psytrance_tracker_backend.model.ArtistProfile;
import com.psytrance.psytrance_tracker_backend.model.ArtistRequest;
import com.psytrance.psytrance_tracker_backend.model.ArtistTrack;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistFestivalRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistOwnerRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistProfileRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRequestRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistTrackRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Artists editing their own profile. An admin links an account to an artist (the owner); the owner and admins edit
 * the profile, tracks and festivals directly. Genres move the artist's satellite for everyone and the name is how
 * line-ups find them, so an owner asks for those and an admin decides. Every change is logged in admin_changes.
 */
@Service
public class ArtistEditing {

    private final ArtistRepository artistRepository;
    private final ArtistOwnerRepository ownerRepository;
    private final ArtistTrackRepository trackRepository;
    private final ArtistFestivalRepository festivalRepository;
    private final ArtistRequestRepository requestRepository;
    private final ArtistProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AdminChangeRepository changeRepository;
    private final ArtistProfiles artistProfiles;
    private final MapPlacements mapPlacements;
    private final MapGenres mapGenres;
    private final Admins admins;
    private final Clock clock;
    private final PageTrust pageTrust;

    public ArtistEditing(ArtistRepository artistRepository, ArtistOwnerRepository ownerRepository,
                         ArtistTrackRepository trackRepository, ArtistFestivalRepository festivalRepository,
                         ArtistRequestRepository requestRepository, ArtistProfileRepository profileRepository,
                         UserRepository userRepository, AdminChangeRepository changeRepository,
                         ArtistProfiles artistProfiles, MapPlacements mapPlacements, MapGenres mapGenres,
                         Admins admins, Clock clock, PageTrust pageTrust) {
        this.artistRepository = artistRepository;
        this.ownerRepository = ownerRepository;
        this.trackRepository = trackRepository;
        this.festivalRepository = festivalRepository;
        this.requestRepository = requestRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.changeRepository = changeRepository;
        this.artistProfiles = artistProfiles;
        this.mapPlacements = mapPlacements;
        this.mapGenres = mapGenres;
        this.admins = admins;
        this.clock = clock;
        this.pageTrust = pageTrust;
    }

    // ---- What the owner and admins can do ----

    /** Everything the editor shows; the owner's username only for admins */
    public ArtistEditDto edit(String slug, String username) {
        Artist artist = requireEditor(slug, username);
        String owner = admins.isAdmin(username)
                ? ownerRepository.findById(artist.getSlug()).map(o -> o.getUser().getUsername()).orElse(null)
                : null;
        return new ArtistEditDto(artist.getSlug(), artist.getName(),
                profileRepository.findById(artist.getSlug()).map(ArtistProfileDto::from).orElse(ArtistProfileDto.EMPTY),
                tracksOf(artist.getSlug()), festivalsOf(artist.getSlug()),
                mapGenres.genresOfArtist(artist.getName()),
                requestRepository.findByArtistSlugAndStatus(artist.getSlug(), ArtistRequest.Status.PENDING).stream()
                        .map(request -> toDto(request, artist.getName()))
                        .toList(),
                owner,
                profileRepository.findById(artist.getSlug()).map(ArtistProfile::getRole).orElse(null));
    }

    /** The artist's main role, set at once: it is a label and a filter, nothing on the map moves */
    @Transactional
    public String setRole(String slug, String role, String username) {
        Artist artist = requireEditor(slug, username);
        String picked = role == null || role.isBlank() ? null : role.strip().toUpperCase(Locale.ROOT);
        if (picked != null && !ROLES.contains(picked)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The role is DJ, PRODUCER or LIVE");
        }
        ArtistProfile profile = profileOf(artist);
        String before = profile.getRole();
        profile.setRole(picked, clock.instant());
        profileRepository.save(profile);
        log(username, AdminChange.ARTIST_ROLE, artist.getName(), before, picked);
        return picked;
    }

    /** The roles an artist can pick; every role gets the same profile options */
    static final List<String> ROLES = List.of("DJ", "PRODUCER", "LIVE");

    public ArtistProfileDto saveProfile(String slug, ArtistProfileRequest request, String username) {
        requireEditor(slug, username);
        return artistProfiles.save(slug, request, username);
    }

    @Transactional
    public List<ArtistListsRequest.Track> saveTracks(String slug, ArtistListsRequest.Tracks request, String username) {
        Artist artist = requireEditor(slug, username);
        trackRepository.deleteByArtistSlug(artist.getSlug());
        trackRepository.flush();
        List<ArtistListsRequest.Track> tracks = request.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            trackRepository.save(new ArtistTrack(artist.getSlug(), i, tracks.get(i).title().strip(), tracks.get(i).url().strip()));
        }
        log(username, AdminChange.ARTIST_TRACKS, artist.getName(), null,
                summary(tracks.stream().map(ArtistListsRequest.Track::title).toList()));
        return tracksOf(artist.getSlug());
    }

    @Transactional
    public List<ArtistListsRequest.Festival> saveFestivals(String slug, ArtistListsRequest.Festivals request, String username) {
        Artist artist = requireEditor(slug, username);
        festivalRepository.deleteByArtistSlug(artist.getSlug());
        festivalRepository.flush();
        List<ArtistListsRequest.Festival> festivals = request.festivals();
        for (int i = 0; i < festivals.size(); i++) {
            ArtistListsRequest.Festival festival = festivals.get(i);
            String url = festival.url() == null || festival.url().isBlank() ? null : festival.url().strip();
            festivalRepository.save(new ArtistFestival(artist.getSlug(), i, festival.name().strip(), festival.year(), url));
        }
        log(username, AdminChange.ARTIST_FESTIVALS, artist.getName(), null,
                summary(festivals.stream().map(f -> f.year() == null ? f.name() : f.name() + " " + f.year()).toList()));
        return festivalsOf(artist.getSlug());
    }

    /**
     * Asks for other genres or another name. A newer request of the same kind replaces a pending one. An admin's
     * request applies at once, since an admin would approve it anyway.
     */
    @Transactional
    public ArtistRequestDto request(String slug, ArtistChangeRequest change, String username) {
        Artist artist = requireEditor(slug, username);
        String value = switch (change.kind()) {
            case GENRES -> {
                List<String> genres = change.genres() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(change.genres()));
                if (genres.isEmpty() || !genres.stream().allMatch(mapGenres::isGenre)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick one to four genres of the map");
                }
                yield String.join(",", genres);
            }
            case NAME -> {
                String name = change.name() == null ? "" : change.name().strip();
                if (name.length() < 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write the name as it should be shown");
                }
                yield name;
            }
        };
        Instant now = clock.instant();
        for (ArtistRequest pending : requestRepository.findByArtistSlugAndStatus(artist.getSlug(), ArtistRequest.Status.PENDING)) {
            if (pending.getKind() == change.kind()) {
                pending.decide(ArtistRequest.Status.REPLACED, username, now);
            }
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first"));
        ArtistRequest request = requestRepository.save(new ArtistRequest(artist.getSlug(), change.kind(), value, user, now));
        if (pageTrust.trusts(PageTrust.Kind.ARTIST, artist.getSlug(), username)) {
            apply(request, artist, username);
        }
        return toDto(request, artist.getName());
    }

    // ---- Admins ----

    /** Links an account to the artist as its owner (the previous owner, if any, loses it) */
    @Transactional
    public String setOwner(String slug, String ownerUsername, String adminUsername) {
        Artist artist = artist(slug);
        User user = userRepository.findByUsername(ownerUsername == null ? "" : ownerUsername.strip())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account called " + ownerUsername));
        String before = ownerRepository.findById(artist.getSlug()).map(o -> o.getUser().getUsername()).orElse(null);
        ownerRepository.save(new ArtistOwner(artist.getSlug(), user, clock.instant()));
        if (!user.getUsername().equals(before)) {
            pageTrust.reset(PageTrust.Kind.ARTIST, artist.getSlug());
        }
        log(adminUsername, AdminChange.ARTIST_OWNER, artist.getName(), before, user.getUsername());
        return user.getUsername();
    }

    @Transactional
    public void removeOwner(String slug, String adminUsername) {
        Artist artist = artist(slug);
        ownerRepository.findById(artist.getSlug()).ifPresent(owner -> {
            ownerRepository.delete(owner);
            log(adminUsername, AdminChange.ARTIST_OWNER, artist.getName(), owner.getUser().getUsername(), null);
        });
    }

    /** The owners' requests waiting for an admin, newest first */
    public List<ArtistRequestDto> pendingRequests() {
        List<ArtistRequest> requests = requestRepository.findWithUserByStatus(ArtistRequest.Status.PENDING);
        Map<String, String> names = artistRepository.findBySlugIn(requests.stream().map(ArtistRequest::getArtistSlug).toList())
                .stream().collect(Collectors.toMap(Artist::getSlug, Artist::getName));
        return requests.stream()
                .map(request -> toDto(request, names.getOrDefault(request.getArtistSlug(), request.getArtistSlug())))
                .toList();
    }

    @Transactional
    public ArtistRequestDto decide(long requestId, boolean approve, String adminUsername) {
        ArtistRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (request.getStatus() != ArtistRequest.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was decided already");
        }
        Artist artist = artist(request.getArtistSlug());
        if (approve) {
            apply(request, artist, adminUsername);
        } else {
            request.decide(ArtistRequest.Status.REJECTED, adminUsername, clock.instant());
        }
        return toDto(request, artist.getName());
    }

    // ---- The owner's own artists ----

    /** The artists this user owns, for "Edit your profile" */
    public List<ArtistSummary> ownedBy(String username) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    List<String> slugs = ownerRepository.findByUserId(user.getId()).stream().map(ArtistOwner::getArtistSlug).toList();
                    return artistRepository.findBySlugIn(slugs).stream()
                            .map(artist -> new ArtistSummary(artist.getSlug(), artist.getName(), 0L))
                            .toList();
                })
                .orElse(List.of());
    }

    public boolean canEdit(String slug, String username) {
        if (username == null) {
            return false;
        }
        return admins.isAdmin(username) || ownerRepository.findById(ArtistNames.slug(slug))
                .map(owner -> owner.getUser().getUsername().equals(username))
                .orElse(false);
    }

    // ---- Helpers ----

    // Genres go through the map's placements (logged there, with undo); the name is the profile's display name
    private void apply(ArtistRequest request, Artist artist, String adminUsername) {
        if (request.getKind() == ArtistRequest.Kind.GENRES) {
            mapPlacements.setGenres(artist.getName(), List.of(request.getValue().split(",")), adminUsername);
        } else {
            ArtistProfile profile = profileOf(artist);
            String before = profile.getDisplayName();
            profile.setDisplayName(request.getValue(), clock.instant());
            profileRepository.save(profile);
            log(adminUsername, AdminChange.ARTIST_NAME, artist.getName(), before, request.getValue());
        }
        request.decide(ArtistRequest.Status.APPROVED, adminUsername, clock.instant());
    }

    // The artist's profile, a new empty one when nobody wrote one yet
    private ArtistProfile profileOf(Artist artist) {
        return profileRepository.findById(artist.getSlug()).orElseGet(() -> {
            ArtistProfile created = new ArtistProfile(artist.getSlug());
            created.update(null, null, null, null, null, null, null, clock.instant());
            return created;
        });
    }

    // The artist, for an admin or the artist's owner; guests get 401 and other users 403
    private Artist requireEditor(String slug, String username) {
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first");
        }
        Artist artist = artist(slug);
        if (!canEdit(artist.getSlug(), username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the artist and admins can edit this profile");
        }
        return artist;
    }

    private Artist artist(String slug) {
        return artistRepository.findBySlug(ArtistNames.slug(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No artist called " + slug));
    }

    List<ArtistListsRequest.Track> tracksOf(String slug) {
        return trackRepository.findByArtistSlugOrderByPositionAsc(slug).stream()
                .map(track -> new ArtistListsRequest.Track(track.getTitle(), track.getUrl()))
                .toList();
    }

    List<ArtistListsRequest.Festival> festivalsOf(String slug) {
        return festivalRepository.findByArtistSlugOrderByPositionAsc(slug).stream()
                .map(festival -> new ArtistListsRequest.Festival(festival.getName(), festival.getYear(), festival.getUrl()))
                .toList();
    }

    private static ArtistRequestDto toDto(ArtistRequest request, String artistName) {
        return new ArtistRequestDto(request.getId(), request.getArtistSlug(), artistName, request.getKind(),
                request.getValue(), request.getUser().getUsername(), request.getStatus(), request.getCreatedAt());
    }

    // What the history shows of a list: its items, cut to fit the column
    private static String summary(List<String> items) {
        String joined = String.join(", ", items);
        return joined.length() > 2000 ? joined.substring(0, 1997) + "..." : joined;
    }

    private void log(String username, String kind, String target, String before, String after) {
        changeRepository.save(new AdminChange(username, kind, target, before, after, clock.instant()));
    }
}
