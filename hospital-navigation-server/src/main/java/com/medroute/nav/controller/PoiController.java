package com.medroute.nav.controller;

import com.medroute.nav.model.Poi;
import com.medroute.nav.service.DemoGraphService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/hospitals/{hospitalId}/pois")
public class PoiController {
    private final DemoGraphService graphService;

    public PoiController(DemoGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public List<Poi> pois(
        @PathVariable long hospitalId,
        @RequestParam(required = false) String keyword
    ) {
        return graphService.pois(hospitalId, keyword);
    }
}
