package com.medroute.nav.dto;

import java.util.List;
import java.util.UUID;

public record NavigationPoiSearchResponse(
    UUID releaseId,
    List<Poi> items,
    String nextPageToken
) {
    public record Poi(
        UUID id,
        String code,
        String name,
        String category,
        UUID floorId,
        String floorCode,
        double x,
        double y,
        boolean accessible,
        List<String> searchKeywords
    ) {
    }
}
