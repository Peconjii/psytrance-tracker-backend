-- What an organizer's page shows besides their events, written by the organizer (their owner) or an admin
alter table organizers add column photo_url varchar(500);
alter table organizers add column bio varchar(1000);
alter table organizers add column instagram_url varchar(300);
alter table organizers add column website_url varchar(300);

-- The account that owns an organizer's page, linked by an admin like an artist's owner. The owner edits the page
-- and the timetables of the organizer's events.
create table organizer_owners (
    organizer_slug varchar(100)                not null,
    user_id        bigint                      not null references app_user (id),
    created_at     timestamp(6) with time zone not null,
    primary key (organizer_slug)
);

create index ix_organizer_owners_user on organizer_owners (user_id);
