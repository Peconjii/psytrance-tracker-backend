-- Line-up words admins marked as "not an artist" ("Lounge", "Coming Soon"...): the artist index skips them.
create table artist_blocklist (
    slug varchar(100) not null,
    name varchar(100) not null,
    primary key (slug)
);

-- Two spellings of one artist ("AJJA" and "Ajja Experience"): the line-up spelling alias_slug is stored as the artist.
create table artist_aliases (
    alias_slug varchar(100) not null,
    artist_id  bigint       not null references artists (id),
    primary key (alias_slug)
);
