package com.medroute.nav.navigation.service;

import java.util.UUID;

public class ReleaseMismatchException extends RuntimeException {
    private final UUID expectedReleaseId;
    private final UUID activeReleaseId;

    public ReleaseMismatchException(UUID expectedReleaseId, UUID activeReleaseId) {
        super(
            "Expected release " + expectedReleaseId
                + " but active release is " + activeReleaseId
        );
        this.expectedReleaseId = expectedReleaseId;
        this.activeReleaseId = activeReleaseId;
    }

    public UUID expectedReleaseId() {
        return expectedReleaseId;
    }

    public UUID activeReleaseId() {
        return activeReleaseId;
    }
}
