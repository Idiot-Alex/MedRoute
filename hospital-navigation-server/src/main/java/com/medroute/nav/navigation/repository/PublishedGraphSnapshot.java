package com.medroute.nav.navigation.repository;

import com.medroute.nav.navigation.model.NavigationGraph;

import java.time.Instant;
import java.util.Objects;

public record PublishedGraphSnapshot(
    NavigationGraph graph,
    String buildingCode,
    String buildingName,
    String releaseCode,
    Instant publishedAt
) {
    public PublishedGraphSnapshot {
        graph = Objects.requireNonNull(graph, "graph");
        buildingCode = Objects.requireNonNull(buildingCode, "buildingCode");
        buildingName = Objects.requireNonNull(buildingName, "buildingName");
        releaseCode = Objects.requireNonNull(releaseCode, "releaseCode");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
    }
}
