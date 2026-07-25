package com.medroute.nav.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminWorkspaceResponse(
    Building building,
    Release release,
    List<Floor> floors,
    DraftGraphPayload graph
) {
    public AdminWorkspaceResponse {
        floors = floors == null ? List.of() : List.copyOf(floors);
    }

    public record Building(
        UUID id,
        String code,
        String name
    ) {
    }

    public record Release(
        UUID id,
        String code,
        String status,
        long contentRevision,
        UUID basedOnReleaseId,
        String description,
        String createdBy,
        Instant createdAt,
        String publishedBy,
        Instant publishedAt
    ) {
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
