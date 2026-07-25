package com.medroute.nav.controller;

import com.medroute.nav.dto.NavigationRouteRequest;
import com.medroute.nav.dto.NavigationRouteResponse;
import com.medroute.nav.navigation.service.MultiFloorRouteService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final MultiFloorRouteService routeService;

    public RouteController(MultiFloorRouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public NavigationRouteResponse route(
        @RequestBody NavigationRouteRequest request
    ) {
        return routeService.calculateRoute(request);
    }
}
