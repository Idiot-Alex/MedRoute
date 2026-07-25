package com.medroute.nav.navigation.model;

import java.util.UUID;

public record GraphNode(
    UUID id,
    String code,
    UUID floorId,
    double x,
    double y,
    String type
) {
}
