package com.medroute.nav.controller;

import com.medroute.nav.dto.RouteRequest;
import com.medroute.nav.dto.RouteResponse;
import com.medroute.nav.service.RouteService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public RouteResponse route(@RequestBody RouteRequest request) {
        return routeService.route(request);
    }
}
