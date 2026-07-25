CREATE UNIQUE INDEX uq_building_active_release
    ON building_map_release(building_id)
    WHERE is_active;

CREATE INDEX idx_override_active_window
    ON edge_state_override(release_id, effective_from, effective_to)
    WHERE revoked_at IS NULL;
