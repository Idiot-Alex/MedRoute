package com.medroute.nav.model;

import java.util.List;

public record MapGraph(
    FloorInfo floor,
    List<PathNode> nodes,
    List<PathEdge> edges,
    List<Poi> pois
) {
}
