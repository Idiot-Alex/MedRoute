package com.medroute.nav.controller;

import com.medroute.nav.dto.AdminRouteRegressionCaseListResponse;
import com.medroute.nav.dto.AdminRouteRegressionCaseResponse;
import com.medroute.nav.dto.RouteRegressionCaseRequest;
import com.medroute.nav.navigation.service.RouteRegressionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@CrossOrigin(
    allowedHeaders = {
        "Content-Type",
        "X-Admin-User",
        "X-Request-Id"
    },
    exposedHeaders = {"X-Request-Id"}
)
@RestController
@RequestMapping("/api/admin")
public class RouteRegressionController {
    private final RouteRegressionService regressionService;

    public RouteRegressionController(
        RouteRegressionService regressionService
    ) {
        this.regressionService = regressionService;
    }

    @GetMapping("/buildings/{buildingId}/route-regression-cases")
    public AdminRouteRegressionCaseListResponse list(
        @PathVariable UUID buildingId
    ) {
        return regressionService.list(buildingId);
    }

    @PostMapping("/buildings/{buildingId}/route-regression-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRouteRegressionCaseResponse create(
        @PathVariable UUID buildingId,
        @RequestBody RouteRegressionCaseRequest request,
        @RequestHeader(
            name = MapAuthoringController.ADMIN_USER_HEADER,
            required = false
        ) String actor
    ) {
        return regressionService.create(buildingId, request, actor);
    }

    @PutMapping("/route-regression-cases/{caseId}")
    public AdminRouteRegressionCaseResponse update(
        @PathVariable UUID caseId,
        @RequestBody RouteRegressionCaseRequest request,
        @RequestHeader(
            name = MapAuthoringController.ADMIN_USER_HEADER,
            required = false
        ) String actor
    ) {
        return regressionService.update(caseId, request, actor);
    }

    @DeleteMapping("/route-regression-cases/{caseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID caseId) {
        regressionService.delete(caseId);
    }
}
