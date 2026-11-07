-- A stage can carry up to four genres in brackets ("Main Stage (Forest, Goa trance, Psy-techno, Dark psy)"), written
-- by our event form and the timetable editor, so the stage name needs more room than 100 characters
alter table event_timetable alter column stage type varchar(200);
