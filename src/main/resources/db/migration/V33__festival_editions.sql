-- Editions of a festival of the map set by hand: an event named differently that is an edition (edition = true),
-- or one whose name matches that is not (edition = false). Without a row the festival page goes by the name.
create table festival_editions
(
    festival_id varchar(100)                not null,
    event_id    bigint                      not null,
    edition     boolean                     not null,
    decided_by  varchar(255)                not null,
    decided_at  timestamp(6) with time zone not null,
    primary key (festival_id, event_id)
);

create index ix_festival_editions_event on festival_editions (event_id);
