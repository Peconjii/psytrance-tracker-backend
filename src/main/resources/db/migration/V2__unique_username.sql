-- User.username is marked unique, but ddl-auto=update never adds a constraint to an existing table,
-- so duplicate usernames were possible (and login would then fail on two matches).
alter table app_user add constraint uk_app_user_username unique (username);
