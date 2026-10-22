-- Labels and festivals an admin adds to the map from the admin panel, next to the ones in labels.json and
-- festivals.json (which only change with a deploy). genres: the map genre ids, comma separated; x, y: where the
-- marker sits in the galaxy, worked out from the genres' stars. A label's crew is in label_crew like any label's.
create table map_labels
(
    name       varchar(200)                not null,
    country    varchar(100),
    since      integer,
    genres     varchar(300)                not null,
    x          integer                     not null,
    y          integer                     not null,
    created_by varchar(255)                not null,
    created_at timestamp(6) with time zone not null,
    primary key (name)
);

create table map_festivals
(
    id          varchar(100)                not null,
    name        varchar(200)                not null,
    country     varchar(100),
    since       integer,
    description varchar(1000),
    genres      varchar(300)                not null,
    x           integer                     not null,
    y           integer                     not null,
    created_by  varchar(255)                not null,
    created_at  timestamp(6) with time zone not null,
    primary key (id)
);
