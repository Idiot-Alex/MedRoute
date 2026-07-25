package com.medroute.nav.controller;

import com.medroute.nav.dto.CreateOperationClosureRequest;
import com.medroute.nav.dto.OperationClosureListResponse;
import com.medroute.nav.navigation.service.OperationManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
public class OperationManagementController {
    private final OperationManagementService service;

    public OperationManagementController(
        OperationManagementService service
    ) {
        this.service = service;
    }

    @GetMapping("/buildings/{buildingId}/operations/closures")
    public OperationClosureListResponse list(
        @PathVariable UUID buildingId
    ) {
        return service.list(buildingId);
    }

    @PostMapping("/buildings/{buildingId}/operations/closures")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationClosureListResponse create(
        @PathVariable UUID buildingId,
        @RequestHeader(
            name = MapAuthoringController.ADMIN_USER_HEADER,
            required = false
        ) String actor,
        @RequestBody CreateOperationClosureRequest request
    ) {
        return service.create(buildingId, request, actor);
    }

    @DeleteMapping("/operations/closures/{closureId}")
    public OperationClosureListResponse revoke(
        @PathVariable UUID closureId,
        @RequestHeader(
            name = MapAuthoringController.ADMIN_USER_HEADER,
            required = false
        ) String actor
    ) {
        return service.revoke(closureId, actor);
    }
}
