-- Cached YouTube aftermovie searches, one row per festival (video_id is null when none was found)
create table aftermovie_lookups (
    festival_key varchar(200)             not null,
    video_id     varchar(20),
    title        varchar(300),
    checked_at   timestamp(6) with time zone not null,
    primary key (festival_key)
);
