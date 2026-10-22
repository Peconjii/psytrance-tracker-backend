-- Roles an admin gives from the admin panel (Admin → Users): ADMIN, or EDITOR (fixes the content: events, genres,
-- added events, reviews; no inbox, accounts, requests or history). Admins from ADMIN_USERNAMES stay admins anyway.
create table user_roles
(
    user_id    bigint                      not null references app_user (id) on delete cascade,
    role       varchar(20)                 not null,
    granted_by varchar(255)                not null,
    granted_at timestamp(6) with time zone not null,
    primary key (user_id)
);
