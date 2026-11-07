-- Genres (stars) an admin adds to the galaxy from the admin panel (AddedGenres), so a new style like hypnotic psy needs
-- no change to map/genres.json and no deploy. links: the genre ids it is drawn connected to, comma separated, the
-- first one is the style it grew out of. keywords: extra words that name it in stage headers, comma separated.
-- Its texts and key tracks live in map_genre_content and its artists in map_artist_genres, like any genre's.
create table map_genres
(
    id         varchar(40)  not null primary key,
    name       varchar(40)  not null,
    family     varchar(10)  not null,
    year       integer      not null,
    bpm_min    integer,
    bpm_max    integer,
    links      varchar(300) not null,
    keywords   varchar(400),
    x          integer      not null,
    y          integer      not null,
    created_by varchar(255),
    created_at timestamp(6) with time zone not null
);
