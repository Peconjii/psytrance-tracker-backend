-- Clicks on an event's "Tickets ↗" / "Goabase ↗" button, one row per event and day. Only admins see them
-- (Admin → Dashboard); organizers and the public never do.
create table ticket_clicks
(
    event_id   varchar(40)  not null,
    day        date         not null,
    event_name varchar(200),
    clicks     integer      not null,
    primary key (event_id, day)
);
