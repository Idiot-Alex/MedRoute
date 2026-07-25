package com.medroute.nav.navigation.service;

import java.util.UUID;

public class ReleaseImmutableException extends RuntimeException {
    private final UUID releaseId;

    public ReleaseImmutableException(UUID releaseId) {
        super("Release is not an editable draft: " + releaseId);
        this.releaseId = releaseId;
    }

    public UUID releaseId() {
        return releaseId;
    }
}
