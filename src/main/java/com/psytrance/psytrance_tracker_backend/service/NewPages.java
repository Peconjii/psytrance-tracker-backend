package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

/**
 * "Not on the map yet?": an account with a confirmed email asks for a page the site doesn't have. An admin decides.
 * Approving an artist or organizer makes the page with the asker as its owner; a label or festival is a constellation
 * or station of the map's data, so approving one only notes it for the next data update.
 */
@Service
public class NewPages {

    public static final List<String> KINDS = PageClaims.KINDS;

    private final JdbcClient jdbc;
    private final ArtistRepository artistRepository;
    private final Organizers organizers;
    private final ArtistEditing artistEditing;
    private final OrganizerEditing organizerEditing;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final DecisionMailer decisionMailer;
    private final MapExtras mapExtras;
    private final LabelPages labelPages;
    private final FestivalPages festivalPages;
    private final Clock clock;

    public NewPages(JdbcClient jdbc, ArtistRepository artistRepository, Organizers organizers, ArtistEditing artistEditing,
                    OrganizerEditing organizerEditing, MapLabels mapLabels, MapFestivals mapFestivals,
                    DecisionMailer decisionMailer, MapExtras mapExtras, LabelPages labelPages,
                    FestivalPages festivalPages, Clock clock) {
        this.jdbc = jdbc;
        this.artistRepository = artistRepository;
        this.organizers = organizers;
        this.artistEditing = artistEditing;
        this.organizerEditing = organizerEditing;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.decisionMailer = decisionMailer;
        this.mapExtras = mapExtras;
        this.labelPages = labelPages;
        this.festivalPages = festivalPages;
        this.clock = clock;
    }

    /**
     * What the asker sends. genres: map genre ids, comma separated (where the satellite or marker could go); since,
     * description, imageUrl: a proposal for the page; artists: a label's artists, comma separated; town: a festival's.
     */
    public record Ask(String kind, String name, String country, String websiteUrl, String proofUrl, String genres,
                      String message, Integer since, String description, String imageUrl, String artists, String town) {

        public Ask(String kind, String name, String country, String websiteUrl, String proofUrl, String genres, String message) {
            this(kind, name, country, websiteUrl, proofUrl, genres, message, null, null, null, null, null);
        }
    }

    /** createdSlug: the page an approved artist or organizer request made (null for labels and festivals) */
    public record Request(long id, String kind, String name, String country, String websiteUrl, String proofUrl,
                          String genres, String message, String username, String email, String status,
                          Instant createdAt, String rejectReason, String createdSlug, Integer since, String description,
                          String imageUrl, String artists, String town) {
    }

    @Transactional
    public Request ask(Ask ask, String username) {
        if (!KINDS.contains(ask.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The kind is one of " + String.join(", ", KINDS));
        }
        String name = ask.name() == null ? "" : ask.name().strip();
        if (ArtistNames.slug(name).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write the name");
        }
        long userId = verifiedUser(username);
        existing(ask.kind(), name).ifPresent(page -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "It's on the site already (" + page + "): open its page and claim it");
        });
        boolean waiting = jdbc.sql("""
                        select count(*) from page_requests
                        where kind = :kind and lower(name) = lower(:name) and user_id = :user and status = 'PENDING'
                        """).param("kind", ask.kind()).param("name", name).param("user", userId).query(Long.class).single() > 0;
        if (waiting) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You asked for this page already; an admin will look at it");
        }
        if (ask.since() != null && (ask.since() < 1960 || ask.since() > Year.now(clock).getValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The year it started is between 1960 and now");
        }
        long id = jdbc.sql("""
                        insert into page_requests (kind, name, country, website_url, proof_url, genres, message, user_id, status,
                                                   created_at, since, description, image_url, artists, town)
                        values (:kind, :name, :country, :website, :proof, :genres, :message, :user, 'PENDING', :now,
                                :since, :description, :image, :artists, :town)
                        returning id
                        """)
                .param("kind", ask.kind()).param("name", name).param("country", blankToNull(ask.country()))
                .param("website", blankToNull(ask.websiteUrl())).param("proof", blankToNull(ask.proofUrl()))
                .param("genres", blankToNull(ask.genres())).param("since", ask.since())
                .param("description", blankToNull(ask.description())).param("image", blankToNull(ask.imageUrl()))
                .param("artists", "LABEL".equals(ask.kind()) ? blankToNull(ask.artists()) : null)
                .param("town", "FESTIVAL".equals(ask.kind()) ? blankToNull(ask.town()) : null)
                .param("message", blankToNull(ask.message())).param("user", userId)
                .param("now", Timestamp.from(clock.instant()))
                .query(Long.class).single();
        return find(id).orElseThrow();
    }

    /** The requests of this account, newest first */
    public List<Request> mine(String username) {
        return jdbc.sql(SELECT + " where u.username = :username order by r.created_at desc")
                .param("username", username).query(this::request).list();
    }

    /** For admins: the requests waiting for a decision, oldest first */
    public List<Request> pending() {
        return jdbc.sql(SELECT + " where r.status = 'PENDING' order by r.created_at").query(this::request).list();
    }

    @Transactional
    public Request decide(long id, boolean approve, String reason, String adminUsername) {
        return decide(id, approve, reason, null, adminUsername);
    }

    /**
     * What a label or festival gets on the map when an admin adds it right away: genres decide where it sits;
     * artists: a label's crew as the admin left it (null: the proposal's)
     */
    public record OnTheMap(String country, Integer since, String description, List<String> genres, List<String> artists) {

        public OnTheMap(String country, Integer since, String description, List<String> genres) {
            this(country, since, description, genres, null);
        }
    }

    /**
     * Approving an artist or organizer makes its page with the asker as owner. A label or festival with
     * {@code onTheMap} is added to the map the same way, owner included; without it, it is only noted.
     */
    @Transactional
    public Request decide(long id, boolean approve, String reason, OnTheMap onTheMap, String adminUsername) {
        Request request = find(id).filter(r -> "PENDING".equals(r.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No waiting request " + id));
        String createdSlug = null;
        if (approve) {
            boolean addNow = onTheMap != null && onTheMap.genres() != null && !onTheMap.genres().isEmpty();
            createdSlug = switch (request.kind()) {
                case "ARTIST" -> createArtist(request, adminUsername);
                case "ORGANIZER" -> createOrganizer(request, adminUsername);
                case "LABEL" -> addNow ? addLabel(request, onTheMap, adminUsername) : null;
                default -> addNow ? addFestival(request, onTheMap, adminUsername) : null;
            };
        }
        jdbc.sql("""
                        update page_requests set status = :status, decided_by = :admin, decided_at = :now,
                            reject_reason = :reason, created_slug = :slug
                        where id = :id
                        """)
                .param("status", approve ? "APPROVED" : "REJECTED").param("admin", adminUsername)
                .param("now", Timestamp.from(clock.instant())).param("reason", approve ? null : blankToNull(reason))
                .param("slug", createdSlug).param("id", id).update();
        Request decided = find(id).orElseThrow();
        String what = "Your request for a page of " + decided.name();
        if (createdSlug != null) {
            decisionMailer.send(decided.email(), what, true,
                    ("LABEL".equals(decided.kind()) || "FESTIVAL".equals(decided.kind()) ? "It is on the map now, " : "The page is made ")
                            + "and it is yours: open it and use \"Edit\".",
                    pathOf(decided.kind(), createdSlug), null);
        } else {
            decisionMailer.send(decided.email(), what, approve,
                    "It goes on the map with the next update of the map's data; then claim it on its page.", null,
                    decided.rejectReason());
        }
        return decided;
    }

    // ---- Helpers ----

    // The page on the site of what was made: createdSlug is the slug, the label's name or the festival's id
    static String pathOf(String kind, String createdSlug) {
        return switch (kind) {
            case "ARTIST" -> "/artists/" + createdSlug;
            case "ORGANIZER" -> "/organizers/" + createdSlug;
            case "LABEL" -> "/labels/" + URLEncoder.encode(createdSlug, StandardCharsets.UTF_8).replace("+", "%20");
            default -> "/festivals/" + createdSlug;
        };
    }

    // On the map, with its owner, its page from the proposal (words, logo, links) and its crew: the artists the site
    // knows from the line-ups; the others can be added later, once a line-up names them
    private String addLabel(Request request, OnTheMap onTheMap, String adminUsername) {
        String name = mapExtras.addLabel(request.name(), firstNonBlank(onTheMap.country(), request.country()),
                onTheMap.since(), onTheMap.genres(), adminUsername).name();
        labelPages.setOwner(name, request.username(), adminUsername);
        String website = request.websiteUrl();
        boolean bandcamp = website != null && website.toLowerCase(Locale.ROOT).contains("bandcamp.com");
        labelPages.saveProfile(name, new LabelPages.Profile(firstNonBlank(onTheMap.description(), request.description()),
                request.imageUrl(), bandcamp ? null : website, bandcamp ? website : null, null), adminUsername);
        // Checked first rather than caught: a refused crew change would roll the whole decision back
        List<String> artists = onTheMap.artists() != null ? onTheMap.artists() : namesIn(request.artists());
        Set<String> added = new HashSet<>();
        for (String artist : artists) {
            String slug = ArtistNames.slug(artist);
            if (!slug.isEmpty() && added.add(slug) && count("select count(*) from artists where slug = :slug", slug) > 0) {
                labelPages.requestCrew(name, "ADD", artist, adminUsername);
            }
        }
        return name;
    }

    private String addFestival(Request request, OnTheMap onTheMap, String adminUsername) {
        String description = firstNonBlank(onTheMap.description(), request.description());
        String festivalId = mapExtras.addFestival(request.name(), firstNonBlank(onTheMap.country(), request.country()),
                onTheMap.since(), description, onTheMap.genres(), adminUsername).id();
        festivalPages.setOwner(festivalId, request.username(), adminUsername);
        festivalPages.saveProfile(festivalId, new FestivalPages.Profile(description, request.imageUrl(), request.websiteUrl(), null, null, null),
                adminUsername);
        return festivalId;
    }

    // "Ajja, Tristan\nLiquid Soul" -> [Ajja, Tristan, Liquid Soul]
    private static List<String> namesIn(String text) {
        return text == null ? List.of() : Arrays.stream(text.split("[,\\n]")).map(String::strip).filter(n -> !n.isEmpty()).toList();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    // The page and its owner; when the line-ups brought the artist in meanwhile, the asker becomes that page's owner
    private String createArtist(Request request, String adminUsername) {
        String slug = ArtistNames.slug(request.name());
        if (artistRepository.findBySlugIn(List.of(slug)).isEmpty()) {
            artistRepository.save(new Artist(slug, request.name()));
        }
        // The photo and words from the request, unless the page has its own already
        if (request.imageUrl() != null || request.description() != null) {
            jdbc.sql("""
                            insert into artist_profiles (slug, photo_url, bio, updated_at) values (:slug, :photo, :bio, :now)
                            on conflict (slug) do update set photo_url = coalesce(artist_profiles.photo_url, excluded.photo_url),
                              bio = coalesce(artist_profiles.bio, excluded.bio)
                            """)
                    .param("slug", slug).param("photo", request.imageUrl()).param("bio", request.description())
                    .param("now", Timestamp.from(clock.instant())).update();
        }
        artistEditing.setOwner(slug, request.username(), adminUsername);
        return slug;
    }

    private String createOrganizer(Request request, String adminUsername) {
        String slug = organizers.record(request.name(), null);
        if (slug == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name can't be an organizer's page");
        }
        if (request.websiteUrl() != null) {
            jdbc.sql("update organizers set website_url = :website where slug = :slug and website_url is null")
                    .param("website", request.websiteUrl().length() > 300 ? null : request.websiteUrl())
                    .param("slug", slug).update();
        }
        jdbc.sql("""
                        update organizers set photo_url = coalesce(photo_url, :photo), bio = coalesce(bio, :bio) where slug = :slug
                        """)
                .param("photo", request.imageUrl()).param("bio", request.description()).param("slug", slug).update();
        organizerEditing.setOwner(slug, request.username(), adminUsername);
        return slug;
    }

    // Where the page is when the site has it already
    private Optional<String> existing(String kind, String name) {
        String slug = ArtistNames.slug(name);
        return switch (kind) {
            case "ARTIST" -> count("select count(*) from artists where slug = :slug", slug) > 0
                    ? Optional.of("/artists/" + slug) : Optional.empty();
            case "ORGANIZER" -> count("select count(*) from organizers where slug = :slug", slug) > 0
                    ? Optional.of("/organizers/" + slug) : Optional.empty();
            case "LABEL" -> mapLabels.names().stream().filter(label -> ArtistNames.slug(label).equals(slug))
                    .findFirst().map(label -> "/labels/" + label);
            default -> mapFestivals.idOf(name).map(id -> "/festivals/" + id);
        };
    }

    private static final String SELECT = """
            select r.id, r.kind, r.name, r.country, r.website_url, r.proof_url, r.genres, r.message, u.username, u.email,
                   r.status, r.created_at, r.reject_reason, r.created_slug, r.since, r.description, r.image_url, r.artists, r.town
            from page_requests r join app_user u on u.id = r.user_id
            """;

    private Optional<Request> find(long id) {
        return jdbc.sql(SELECT + " where r.id = :id").param("id", id).query(this::request).optional();
    }

    private Request request(ResultSet rs, int row) throws SQLException {
        return new Request(rs.getLong("id"), rs.getString("kind"), rs.getString("name"), rs.getString("country"),
                rs.getString("website_url"), rs.getString("proof_url"), rs.getString("genres"), rs.getString("message"),
                rs.getString("username"), rs.getString("email"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("reject_reason"), rs.getString("created_slug"),
                (Integer) rs.getObject("since"), rs.getString("description"), rs.getString("image_url"),
                rs.getString("artists"), rs.getString("town"));
    }

    private long verifiedUser(String username) {
        record User(long id, boolean verified) {
        }
        User user = jdbc.sql("select id, email_verified from app_user where username = :username")
                .param("username", username).query((rs, row) -> new User(rs.getLong(1), rs.getBoolean(2))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (!user.verified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Confirm your email first: open the link we sent you");
        }
        return user.id();
    }

    private long count(String sql, String slug) {
        return jdbc.sql(sql).param("slug", slug).query(Long.class).single();
    }
}
