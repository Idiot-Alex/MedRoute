package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.CreateOperationClosureRequest;
import com.medroute.nav.dto.OperationClosureListResponse;
import com.medroute.nav.navigation.repository.JdbcOperationStatusProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationManagementService {
    private static final Set<String> TARGET_TYPES = Set.of(
        "path_edge",
        "vertical_link",
        "vertical_connector"
    );

    private final JdbcOperationStatusProvider repository;
    private final Clock clock;

    public OperationManagementService(
        JdbcOperationStatusProvider repository,
        Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    public OperationClosureListResponse list(UUID buildingId) {
        return repository.listClosures(buildingId, clock.instant());
    }

    public OperationClosureListResponse create(
        UUID buildingId,
        CreateOperationClosureRequest request,
        String actor
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!TARGET_TYPES.contains(request.targetType())) {
            throw new IllegalArgumentException(
                "targetType must be path_edge, vertical_link, or "
                    + "vertical_connector"
            );
        }
        if (request.targetId() == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        if (
            request.reason() == null
                || request.reason().isBlank()
                || request.reason().trim().length() > 500
        ) {
            throw new IllegalArgumentException(
                "reason is required and must not exceed 500 characters"
            );
        }
        Instant currentTime = clock.instant();
        Instant effectiveFrom = request.effectiveFrom() == null
            ? currentTime
            : request.effectiveFrom();
        if (
            request.effectiveTo() != null
                && !request.effectiveTo().isAfter(effectiveFrom)
        ) {
            throw new IllegalArgumentException(
                "effectiveTo must be after effectiveFrom"
            );
        }
        return repository.createClosure(
            buildingId,
            request,
            actor(actor),
            currentTime
        );
    }

    public OperationClosureListResponse revoke(
        UUID closureId,
        String actor
    ) {
        return repository.revokeClosure(
            closureId,
            actor(actor),
            clock.instant()
        );
    }

    private String actor(String value) {
        if (value == null || value.isBlank()) {
            return "local-admin";
        }
        String actor = value.trim();
        if (actor.length() > 100) {
            throw new IllegalArgumentException(
                "admin user must not exceed 100 characters"
            );
        }
        return actor;
    }
}
