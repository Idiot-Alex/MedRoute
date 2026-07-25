package com.medroute.nav.controller;

import com.medroute.nav.dto.NavigationContextResponse;
import com.medroute.nav.dto.NavigationPoiSearchResponse;
import com.medroute.nav.navigation.service.NavigationContextService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@CrossOrigin
@RestController
@RequestMapping("/api/buildings")
public class NavigationContextController {
    private final NavigationContextService contextService;

    public NavigationContextController(
        NavigationContextService contextService
    ) {
        this.contextService = contextService;
    }

    @GetMapping("/{buildingId}/navigation-context")
    public NavigationContextResponse activeContext(
        @PathVariable UUID buildingId
    ) {
        return contextService.activeContext(buildingId);
    }

    @GetMapping("/{buildingId}/pois")
    public NavigationPoiSearchResponse activePois(
        @PathVariable UUID buildingId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) UUID floorId,
        @RequestParam(required = false) String category
    ) {
        return contextService.activePois(
            buildingId,
            keyword,
            floorId,
            category
        );
    }
}
