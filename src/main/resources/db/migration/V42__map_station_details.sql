-- What the map says about a label or festival station, fixed without a deploy (StationDetails): only the fields
-- someone changed; the rest still comes from map/labels.json and map/festivals.json (or map_labels / map_festivals
-- for the ones added from the panel). kind: LABEL (key = the label's name) or FESTIVAL (key = its id).
-- genres: genre ids, comma separated. A label keeps its name (pages, follows and crews hang on it); a festival
-- can be renamed, and its former name keeps pointing to it.
create table map_station_details
(
    kind        varchar(10)  not null,
    key         varchar(120) not null,
    name        varchar(80),
    country     varchar(100),
    since       integer,
    description text,
    genres      varchar(400),
    updated_by  varchar(255),
    updated_at  timestamp(6) with time zone,
    primary key (kind, key)
);
