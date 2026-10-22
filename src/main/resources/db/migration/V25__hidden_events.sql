-- Events an admin hid (not psytrance, a duplicate...): they leave the lists, the map, the artist index and the
-- reminders, but are kept here so they can be shown again.
create table hidden_events (
    event_id   bigint                      not null,
    name       varchar(300)                not null,
    reason     varchar(300),
    hidden_by  varchar(255)                not null,
    hidden_at  timestamp(6) with time zone not null,
    primary key (event_id)
);
