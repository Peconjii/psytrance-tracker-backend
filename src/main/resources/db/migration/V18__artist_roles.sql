-- Whether the line-up wrote "live" next to the artist ("Astrix live", "02:15 Live M3dra"): mostly live gigs make a
-- live act, when the artist hasn't picked a role themselves
alter table artist_events add column live boolean not null default false;

-- The artist's main role, picked by the artist (their owner) or an admin: DJ, PRODUCER or LIVE.
-- Null: not picked, so the site guesses it. It is a label and a filter; every artist gets the same profile options.
alter table artist_profiles add column role varchar(20);
