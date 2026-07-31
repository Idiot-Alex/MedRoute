package com.medroute.nav.navigation.repository;

import com.medroute.nav.navigation.service.NavigationResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcFloorMapAssetRepository {
    private final JdbcTemplate jdbc;

    public JdbcFloorMapAssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public FloorMapAsset findActivePublished(UUID revisionId) {
        return find(
            revisionId,
            """
            SELECT
                fma.content,
                fmr.mime_type,
                fmr.sha256
            FROM floor_map_revision fmr
            JOIN floor_map_asset fma
                ON fma.floor_map_revision_id = fmr.id
            WHERE fmr.public_id = ?
              AND EXISTS (
                  SELECT 1
                  FROM release_floor_map rfm
                  JOIN building_map_release r
                      ON r.id = rfm.release_id
                  WHERE rfm.floor_map_revision_id = fmr.id
                    AND r.status = 'published'
                    AND r.is_active = TRUE
              )
            """,
            "Unknown active published floor map revision: "
        );
    }

    public FloorMapAsset findForAdmin(UUID revisionId) {
        return find(
            revisionId,
            """
            SELECT
                fma.content,
                fmr.mime_type,
                fmr.sha256
            FROM floor_map_revision fmr
            JOIN floor_map_asset fma
                ON fma.floor_map_revision_id = fmr.id
            WHERE fmr.public_id = ?
            """,
            "Unknown uploaded floor map revision: "
        );
    }

    private FloorMapAsset find(
        UUID revisionId,
        String query,
        String notFoundMessage
    ) {
        List<FloorMapAsset> assets = jdbc.query(
            query,
            (rs, rowNum) -> new FloorMapAsset(
                rs.getBytes("content"),
                rs.getString("mime_type"),
                rs.getString("sha256")
            ),
            revisionId
        );
        if (assets.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                notFoundMessage + revisionId
            );
        }
        return assets.get(0);
    }

    public record FloorMapAsset(
        byte[] content,
        String mimeType,
        String sha256
    ) {
    }
}
