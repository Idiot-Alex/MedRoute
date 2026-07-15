package com.medroute.nav.model;

public record FloorInfo(
    long hospitalId,
    String hospitalName,
    long buildingId,
    String buildingName,
    long floorId,
    String floorName,
    int imageWidth,
    int imageHeight,
    String version
) {
}
