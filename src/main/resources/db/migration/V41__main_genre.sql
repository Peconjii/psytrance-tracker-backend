-- An artist orbits only one star on the galaxy: their main genre. The other genres still count everywhere else
-- (ships, filters, the profile). Until now the first genre an artist was placed in is the main one.
alter table map_artist_genres add column main boolean not null default false;

update map_artist_genres m set main = true
where m.id = (select min(x.id) from map_artist_genres x where x.artist_name = m.artist_name);
