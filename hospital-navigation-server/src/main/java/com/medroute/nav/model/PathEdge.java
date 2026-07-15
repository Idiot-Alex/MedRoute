package com.medroute.nav.model;

public record PathEdge(
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
    public boolean enabled() {
        return status == null || status.isBlank() || "enabled".equals(status);
    }
}
