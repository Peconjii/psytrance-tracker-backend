-- Festival Buddies: people going to the same event can see each other, but only those who turn it on themselves.
-- Off by default. Country, note and Instagram are optional and only ever shown to other visible people going to the same event.
alter table app_user
    add column buddy_visible boolean     not null default false,
    add column country       varchar(2),
    add column buddy_note    varchar(140),
    add column instagram     varchar(30);
