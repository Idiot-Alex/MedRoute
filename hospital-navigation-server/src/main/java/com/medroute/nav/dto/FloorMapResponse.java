package com.medroute.nav.dto;

public record FloorMapResponse(
    long floorId,
    String name,
    String imageUrl,
    int imageWidth,
    int imageHeight
) {
}
