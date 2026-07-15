package com.medroute.nav.model;

import java.util.List;

public record Poi(
    String id,
    String name,
    String category,
    String nodeId,
    double x,
    double y,
    List<String> searchKeywords
) {
}
