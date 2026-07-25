package com.medroute.nav.navigation.service;

import java.util.UUID;

public class PoiNotInReleaseException extends RuntimeException {
    private final String fieldName;
    private final UUID poiId;

    public PoiNotInReleaseException(String fieldName, UUID poiId) {
        super(fieldName + " does not belong to the active release: " + poiId);
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
