-- Organizers (crews, promoters) of the events: the "captains" of the ships. Built from the organizer Goabase gives
-- for each event, not from the line-up text, and from names admins moved out of the artists ("This is an organizer").
create table organizers (
    slug       varchar(100)                not null,
    name       varchar(200)                not null,
    url        varchar(500),
    created_at timestamp(6) with time zone not null,
    primary key (slug)
);

-- Which organizer ran each event whose line-up was read
alter table event_summaries add column organizer_slug varchar(100);

create index ix_event_summaries_organizer on event_summaries (organizer_slug);

-- Organizers and the "live" marks (V18) come from reading line-ups: read them all again soon, like V15
update lineup_scans set scanned_at = timestamp with time zone '1970-01-01 00:00:00+00';
