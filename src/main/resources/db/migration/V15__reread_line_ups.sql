-- Line-ups read before the map genres (V7), timetables (V12) and blank-name cleanup existed have none of those,
-- and would only be read again after two days. Marked as read long ago, the artist index reads them first.
update lineup_scans set scanned_at = timestamp with time zone '1970-01-01 00:00:00+00';
