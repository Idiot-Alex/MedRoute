package com.medroute.nav.model;

public record PathNode(
    String id,
    double x,
    double y,
    String name,
    String type
) {
}
