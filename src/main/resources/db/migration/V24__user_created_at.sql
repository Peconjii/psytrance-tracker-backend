-- When an account was made, for Admin → Users. Accounts made before this have no date.
alter table app_user add column created_at timestamp(6) with time zone;
