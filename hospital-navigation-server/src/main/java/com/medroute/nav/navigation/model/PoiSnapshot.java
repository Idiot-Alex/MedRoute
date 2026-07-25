package com.medroute.nav.navigation.model;

import java.util.List;
import java.util.UUID;

public record PoiSnapshot(
    UUID id,
    String code,
    String name,
    String category,
    UUID floorId,
    UUID nodeId,
    double x,
    double y,
    boolean accessible,
    List<String> searchKeywords
) {
    public PoiSnapshot {
        searchKeywords = searchKeywords == null ? List.of() : List.copyOf(searchKeywords);
    }
}
