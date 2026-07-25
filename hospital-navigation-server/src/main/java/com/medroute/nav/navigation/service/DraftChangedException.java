package com.medroute.nav.navigation.service;

public class DraftChangedException extends RuntimeException {
    private final long expectedRevision;
    private final long actualRevision;

    public DraftChangedException(long expectedRevision, long actualRevision) {
        super(
            "Expected draft revision " + expectedRevision
                + " but current revision is " + actualRevision
        );
        this.expectedRevision = expectedRevision;
        this.actualRevision = actualRevision;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long actualRevision() {
        return actualRevision;
    }
}
