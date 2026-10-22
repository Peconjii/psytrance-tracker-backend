-- The weekly email (Mondays): where the artists and labels an account follows play in the next two weeks.
-- On unless turned off on the profile; weekly_digest_sent_on keeps it to one a week.
alter table app_user add column weekly_digest boolean not null default true;
alter table app_user add column weekly_digest_sent_on date;
