package com.medroute.nav.model;

public record PathEdge(
    String id,
    String from,
    String to,
    EdgeDirection direction,
    double distance,
    int walkTime,
    boolean accessible,
    String status,
    String type,
    String remark
) {
    public PathEdge {
        direction = direction == null ? EdgeDirection.BOTH : direction;
    }

    public PathEdge(
        String id,
        String from,
        String to,
        double distance,
        int walkTime,
        boolean accessible,
        String status,
        String type,
        String remark
    ) {
        this(
            id,
            from,
            to,
            EdgeDirection.BOTH,
            distance,
            walkTime,
            accessible,
            status,
            type,
            remark
        );
    }

    public boolean enabled() {
        return status == null || status.isBlank() || "enabled".equals(status);
    }
}
