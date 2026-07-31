-- Common fixture migrations insert stable, explicit IDs. PostgreSQL identity
-- sequences are not advanced by those inserts, so move each affected sequence
-- past the largest stored ID before the application creates more rows.
--
-- Never rewind a sequence that is already ahead of the table. This keeps the
-- migration safe for upgraded databases where IDs may have been allocated and
-- later deleted, and also makes the migration idempotent.
DO $$
DECLARE
    target_schema TEXT := current_schema();
    target_table TEXT;
    identity_sequence REGCLASS;
    maximum_id BIGINT;
    sequence_last_value BIGINT;
    sequence_is_called BOOLEAN;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'hospital',
        'campus',
        'building',
        'floor',
        'floor_map_revision',
        'building_map_release',
        'path_node',
        'path_edge',
        'vertical_connector',
        'connector_stop',
        'vertical_link',
        'poi',
        'building_route_regression_case'
    ]
    LOOP
        identity_sequence := to_regclass(
            pg_get_serial_sequence(
                format('%I.%I', target_schema, target_table),
                'id'
            )
        );

        IF identity_sequence IS NULL THEN
            RAISE EXCEPTION
                'Identity sequence not found for %.%.id',
                target_schema,
                target_table;
        END IF;

        EXECUTE format(
            'SELECT MAX(id) FROM %I.%I',
            target_schema,
            target_table
        ) INTO maximum_id;

        EXECUTE format(
            'SELECT last_value, is_called FROM %s',
            identity_sequence
        ) INTO sequence_last_value, sequence_is_called;

        IF maximum_id IS NOT NULL
            AND (NOT sequence_is_called OR sequence_last_value < maximum_id)
        THEN
            PERFORM setval(identity_sequence, maximum_id, TRUE);
        END IF;
    END LOOP;
END
$$;
