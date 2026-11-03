-- "Sign in with Google": the Google account (its stable id, the token's "sub") an account signs in with, if any
alter table app_user add column google_id varchar(255) unique;
