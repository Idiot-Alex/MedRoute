package com.medroute.nav.navigation.service;

import java.util.UUID;

public class RouteEndpointNotAccessibleException extends RuntimeException {
    private final String fieldName;
    private final UUID poiId;

    public RouteEndpointNotAccessibleException(
        String fieldName,
        UUID poiId
    ) {
        super(fieldName + " is not accessible: " + poiId);
        this.fieldName = fieldName;
        this.poiId = poiId;
    }

    public String fieldName() {
        return fieldName;
    }

    public UUID poiId() {
        return poiId;
    }
}
