-- An admin's answer to a feedback message, emailed to its sender and kept with the message
alter table feedback add column reply_text varchar(3000);
alter table feedback add column replied_at timestamp(6) with time zone;
alter table feedback add column replied_by varchar(255);
