-- Mittai Music (India, since 2022) joins the map: its artists orbit the genres the label tags their releases with on
-- Bandcamp (mittaimusic.bandcamp.com, read 22.10.2026). genres.json only fills the placements of a new database, so
-- they are placed here too. Edison (tags unclear) and Babalos (a remix only) are left out on purpose.
-- Only in a database that has placements already: an empty table is filled from genres.json on the first start, and
-- rows put in before that would stop it.
insert into map_artist_genres (artist_name, genre_id)
select artist_name, genre_id from (values
    ('Acajou', 'hitech'), ('Amras', 'hitech'), ('Astroalien', 'hitech'), ('Atim', 'hitech'), ('Calabi Yau', 'hitech'),
    ('Inner Coma', 'hitech'), ('Nabhit', 'hitech'), ('Nibiru', 'hitech'), ('Nost', 'hitech'), ('Paralocks', 'hitech'),
    ('Psytohigh', 'hitech'), ('Sonic System', 'hitech'), ('Spirituvel', 'hitech'),
    ('Mokiru', 'dark'), ('Vox Fabri', 'dark')) as mittai (artist_name, genre_id)
where exists (select 1 from map_artist_genres)
on conflict (artist_name, genre_id) do nothing;

-- Events where they play get their genres worked out again in the next runs of the artist index
update lineup_scans set scanned_at = timestamp with time zone '1970-01-01 00:00:00+00'
where event_id in (
    select ae.event_id from artist_events ae join artists a on a.id = ae.artist_id
    where a.slug in ('acajou', 'amras', 'astroalien', 'atim', 'calabi-yau', 'inner-coma', 'nabhit', 'nibiru', 'nost',
                     'paralocks', 'psytohigh', 'sonic-system', 'spirituvel', 'mokiru', 'vox-fabri'));
