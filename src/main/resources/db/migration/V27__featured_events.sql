-- Events an admin features on the map (a few at a time): their ship is marked and they lead the departures
create table featured_events (
    event_id    bigint                      not null,
    name        varchar(300)                not null,
    note        varchar(200),
    featured_by varchar(255)                not null,
    featured_at timestamp(6) with time zone not null,
    primary key (event_id)
);
