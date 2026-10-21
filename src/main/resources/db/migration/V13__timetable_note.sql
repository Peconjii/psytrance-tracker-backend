-- What a timetable line says after the artist's name ("• Twilight/Forest", "- Nano Rec - UK"). Organizers often name
-- the set's genre there; it decides the set's genre when the artist isn't on the map.
alter table event_timetable add column note varchar(200);
