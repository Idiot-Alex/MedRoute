package com.medroute.nav.dto;

import com.medroute.nav.model.FloorInfo;
import com.medroute.nav.model.PathEdge;
import com.medroute.nav.model.PathNode;
import com.medroute.nav.model.Poi;

import java.util.List;

public record MapGraphResponse(
    FloorInfo floor,
    List<PathNode> nodes,
    List<PathEdge> edges,
    List<Poi> pois
) {
}
