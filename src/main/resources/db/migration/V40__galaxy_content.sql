-- What the galaxy says about each genre: its texts and key tracks. Filled from map/genres.json on first start,
-- then edited in the admin panel (Admin -> Galaxy), so a fix needs no deploy. tracks: a JSON list in the order
-- the panel shows them ([{"artist", "title", "year", "youtube", "famous", "verified", "drop"}]; drop = the second
-- the drop comes, for the quiz). verified_by / verified_at: who checked the genre's texts, and when.
create table map_genre_content
(
    genre_id    varchar(30) primary key,
    description text                        not null,
    bass        text,
    kick        text,
    atmosphere  text,
    year        integer,
    bpm_min     integer,
    bpm_max     integer,
    tracks      text                        not null,
    verified_by varchar(255),
    verified_at timestamp(6) with time zone,
    updated_by  varchar(255),
    updated_at  timestamp(6) with time zone
);

-- A genre's whole content is logged before and after a change, so it can be undone; that is longer than 2000
alter table admin_changes alter column before_value type text;
alter table admin_changes alter column after_value type text;
