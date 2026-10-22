-- What an artist's page shows besides their gigs: a photo, a short bio, links and a booking address.
-- Keyed by the artist's slug (artists.slug), so a profile can be written before the artist is in a line-up.
create table artist_profiles (
    slug           varchar(100)                not null,
    photo_url      varchar(500),
    bio            varchar(1000),
    soundcloud_url varchar(300),
    bandcamp_url   varchar(300),
    instagram_url  varchar(300),
    website_url    varchar(300),
    booking_email  varchar(255),
    updated_at     timestamp(6) with time zone not null,
    primary key (slug)
);

-- Name, dates and place of every event whose line-up was read, so an artist's past gigs can still be listed
-- after the event has left Goabase's list of upcoming events
create table event_summaries (
    event_id   bigint       not null,
    name       varchar(255) not null,
    date_start date,
    date_end   date,
    town       varchar(255),
    country    varchar(255),
    primary key (event_id)
);
