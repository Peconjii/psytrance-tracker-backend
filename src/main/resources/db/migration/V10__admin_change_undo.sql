-- An undo is a change of its own that puts the "before" of an earlier change back; undo_of points to that
-- earlier change. A change can be undone only once.
alter table admin_changes add column undo_of bigint references admin_changes (id);
create unique index uk_admin_changes_undo_of on admin_changes (undo_of);
