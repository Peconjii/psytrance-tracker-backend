package com.psytrance.psytrance_tracker_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Pictures people upload for their page or event: a photo, a logo, a flyer. Kept in the database (Render's disk is
 * wiped on every deploy); the browser makes them small before sending. A picture is used by putting its address in
 * a picture field like any link, so every place that shows pictures works unchanged.
 */
@Service
public class Images {

    /** The biggest picture accepted; the browser sends far less (about 200 KB) */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int UPLOADS_PER_DAY = 30;
    // Where pictures may be used: a picture none of these points to is unused
    private static final String USED = """
            select photo_url from artist_profiles union all select photo_url from organizers
            union all select logo_url from label_profiles union all select logo_url from festival_profiles
            union all select image_url from our_events union all select image_url from page_requests
            """;

    private final JdbcClient jdbc;
    private final FormLimits formLimits;
    private final Clock clock;
    private final String backendUrl;

    public Images(JdbcClient jdbc, FormLimits formLimits, Clock clock, @Value("${app.backend-url}") String backendUrl) {
        this.jdbc = jdbc;
        this.formLimits = formLimits;
        this.clock = clock;
        this.backendUrl = backendUrl.replaceAll("/+$", "");
    }

    public record Image(String contentType, byte[] data) {
    }

    /** Keeps the picture and gives back its address. Only JPEG, PNG and WebP, checked by their first bytes. */
    @Transactional
    public String upload(byte[] data, String username) {
        if (data == null || data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a picture");
        }
        if (data.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The picture is too big (2 MB at most)");
        }
        String type = typeOf(data);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG or WebP pictures");
        }
        formLimits.count("image", username, UPLOADS_PER_DAY, Duration.ofDays(1),
                "You uploaded many pictures today. Try again tomorrow.");
        long id = jdbc.sql("""
                        insert into images (user_id, content_type, size, data, created_at)
                        values ((select id from app_user where username = :username), :type, :size, :data, :now)
                        returning id
                        """)
                .param("username", username).param("type", type).param("size", data.length).param("data", data)
                .param("now", Timestamp.from(clock.instant()))
                .query(Long.class).single();
        return backendUrl + "/api/images/" + id;
    }

    public Optional<Image> find(long id) {
        return jdbc.sql("select content_type, data from images where id = :id").param("id", id)
                .query((rs, row) -> new Image(rs.getString(1), rs.getBytes(2))).optional();
    }

    /**
     * Deletes the pictures nothing points to that are older than a day (replaced ones, ones of deleted accounts,
     * uploads that were never saved). Returns how many went.
     */
    @Transactional
    public int deleteUnused() {
        return jdbc.sql("""
                        delete from images i
                        where i.created_at < :before
                          and not exists (select 1 from (%s) used(url) where used.url like '%%/api/images/' || i.id)
                        """.formatted(USED))
                .param("before", Timestamp.from(clock.instant().minus(Duration.ofDays(1))))
                .update();
    }

    // JPEG starts with FF D8 FF, PNG with 89 'PNG', WebP with 'RIFF' .... 'WEBP'
    static String typeOf(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
