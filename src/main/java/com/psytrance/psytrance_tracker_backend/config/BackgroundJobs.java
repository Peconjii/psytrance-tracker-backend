package com.psytrance.psytrance_tracker_backend.config;

import com.psytrance.psytrance_tracker_backend.service.ArtistFollows;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import com.psytrance.psytrance_tracker_backend.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Work that runs on a timer instead of on a request. Each job is safe to run as often as it likes,
 * because the free server sleeps when nobody uses it and simply misses runs in the meantime.
 * Integration tests turn this off (app.jobs.enabled=false) and call the services directly.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobs {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);

    private final ReminderService reminderService;
    private final ArtistIndexer artistIndexer;
    private final ArtistFollows artistFollows;

    public BackgroundJobs(ReminderService reminderService, ArtistIndexer artistIndexer, ArtistFollows artistFollows) {
        this.reminderService = reminderService;
        this.artistIndexer = artistIndexer;
        this.artistFollows = artistFollows;
    }

    @Scheduled(initialDelayString = "${app.reminders.initial-delay}", fixedDelayString = "${app.reminders.interval}")
    public void sendReminders() {
        try {
            int sent = reminderService.sendDueReminders();
            if (sent > 0) {
                log.info("Sent {} reminder emails", sent);
            }
        } catch (RuntimeException e) {
            // E.g. Goabase down before the first event list was loaded; the next run tries again
            log.warn("Reminder job failed", e);
        }
        // New gigs of followed artists, on the same hourly timer
        try {
            int sent = artistFollows.sendNewGigEmails();
            if (sent > 0) {
                log.info("Sent {} new-gig emails", sent);
            }
        } catch (RuntimeException e) {
            log.warn("New-gig job failed", e);
        }
    }

    @Scheduled(initialDelayString = "${app.artists.initial-delay}", fixedDelayString = "${app.artists.interval}")
    public void indexArtists() {
        try {
            int read = artistIndexer.indexNextBatch();
            if (read > 0) {
                log.info("Read the line-ups of {} events for the artist index", read);
            }
        } catch (RuntimeException e) {
            log.warn("Artist index job failed", e);
        }
    }
}
