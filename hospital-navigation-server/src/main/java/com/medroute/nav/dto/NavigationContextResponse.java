package com.medroute.nav.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only map metadata required to render a published navigation release.
 */
public record NavigationContextResponse(
    Building building,
    Release release,
    List<Floor> floors,
    List<String> supportedRouteModes
) {
    public record Building(UUID id, String code, String name) {
    }

    public record Release(UUID id, String code, Instant publishedAt) {
    }

    public record Floor(
        UUID id,
        String code,
        String name,
        int levelNo,
        MapRevision mapRevision
    ) {
    }

    public record MapRevision(
        UUID id,
        int revisionNo,
        String imageUrl,
        int imageWidth,
        int imageHeight
    ) {
    }
}
