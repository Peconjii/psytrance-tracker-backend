-- "Not on the map yet?" asks for more, so an admin gets a proposal to check instead of a blank: the year it started,
-- a few words about it, a logo or photo, for a label the artists on it and for a festival the town it is in.
-- genres now holds map genre ids ("dark, forest") for every kind, not only for artists.
alter table page_requests add column since integer;
alter table page_requests add column description varchar(1000);
alter table page_requests add column image_url varchar(500);
alter table page_requests add column artists varchar(1000);
alter table page_requests add column town varchar(100);
