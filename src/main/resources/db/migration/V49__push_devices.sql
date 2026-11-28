-- Phones with the Android app that may get push notifications (Firebase Cloud Messaging), one row per phone
create table push_device (
    id         bigserial   primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    token      text        not null unique,
    created_at timestamptz not null,
    seen_at    timestamptz not null
);
create index push_device_user on push_device (user_id);
