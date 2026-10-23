-- A trusted owner's changes to their own page go live without waiting for an admin (see PageTrust).
-- Organizer owners already published their events at once, so they keep that.
alter table artist_owners add column trusted boolean not null default false;
alter table organizer_owners add column trusted boolean not null default false;
alter table label_owners add column trusted boolean not null default false;
alter table festival_owners add column trusted boolean not null default false;
update organizer_owners set trusted = true;
