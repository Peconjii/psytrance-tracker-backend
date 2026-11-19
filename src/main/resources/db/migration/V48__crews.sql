-- Crews: a private group of friends for one event. Joining needs an account and the crew's link (code).
-- Everything goes 7 days after the event ends (cleanup job), and with the owner's account (cascade).
create table crew (
    id          bigserial primary key,
    event_id    bigint       not null,
    event_name  varchar(300) not null,
    event_type  varchar(100),
    starts_on   date         not null,
    ends_on     date         not null,
    name        varchar(60)  not null,
    code        varchar(32)  not null unique,
    owner_id    bigint       not null references app_user (id) on delete cascade,
    camp_spot   varchar(140),
    created_at  timestamptz  not null
);
create index crew_event_idx on crew (event_id);
create index crew_ends_idx on crew (ends_on);

create table crew_member (
    id        bigserial primary key,
    crew_id   bigint      not null references crew (id) on delete cascade,
    user_id   bigint      not null references app_user (id) on delete cascade,
    joined_at timestamptz not null,
    unique (crew_id, user_id)
);
create index crew_member_user_idx on crew_member (user_id);

-- People the captain removed: the same link no longer lets them back in
create table crew_removed (
    crew_id bigint not null references crew (id) on delete cascade,
    user_id bigint not null references app_user (id) on delete cascade,
    primary key (crew_id, user_id)
);

-- Packing list: one row per thing; bringer is who said "I'll bring it"
create table crew_item (
    id         bigserial primary key,
    crew_id    bigint      not null references crew (id) on delete cascade,
    name       varchar(60) not null,
    bringer_id bigint references app_user (id) on delete set null,
    added_by   bigint references app_user (id) on delete set null,
    created_at timestamptz not null
);
create index crew_item_crew_idx on crew_item (crew_id);

-- Must-see sets added by hand, by artist and start (timetable rows are rewritten on every save, so no slot id)
create table crew_pick (
    id          bigserial primary key,
    crew_id     bigint       not null references crew (id) on delete cascade,
    artist_name varchar(100) not null,
    starts_at   timestamp    not null,
    added_by    bigint references app_user (id) on delete set null,
    unique (crew_id, artist_name, starts_at)
);
