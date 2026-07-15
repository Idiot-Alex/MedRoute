package com.medroute.nav.controller;

import com.medroute.nav.dto.FloorMapResponse;
import com.medroute.nav.dto.MapGraphResponse;
import com.medroute.nav.service.DemoGraphService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/hospitals/{hospitalId}/floors/{floorId}")
public class MapController {
    private final DemoGraphService graphService;

    public MapController(DemoGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/map")
    public FloorMapResponse map(
        @PathVariable long hospitalId,
        @PathVariable long floorId
    ) {
        return graphService.floorMap(hospitalId, floorId);
    }

    @GetMapping("/graph")
    public MapGraphResponse graph(
        @PathVariable long hospitalId,
        @PathVariable long floorId
    ) {
        return graphService.floorGraph(hospitalId, floorId);
    }
}
