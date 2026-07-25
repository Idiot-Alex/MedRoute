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

    public FloorMapAsset find(UUID revisionId) {
        List<FloorMapAsset> assets = jdbc.query(
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
            (rs, rowNum) -> new FloorMapAsset(
                rs.getBytes("content"),
                rs.getString("mime_type"),
                rs.getString("sha256")
            ),
            revisionId
        );
        if (assets.isEmpty()) {
            throw new NavigationResourceNotFoundException(
                "Unknown uploaded floor map revision: " + revisionId
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
