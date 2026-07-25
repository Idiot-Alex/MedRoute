CREATE TABLE floor_map_asset (
    floor_map_revision_id BIGINT PRIMARY KEY
        REFERENCES floor_map_revision(id) ON DELETE CASCADE,
    content BYTEA NOT NULL
);
