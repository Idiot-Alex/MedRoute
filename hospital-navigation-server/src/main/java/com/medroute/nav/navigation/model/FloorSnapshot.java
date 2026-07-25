package com.medroute.nav.navigation.model;

import java.util.UUID;

public record FloorSnapshot(
    UUID id,
    String code,
    String name,
    int levelNo,
    UUID mapRevisionId,
    int mapRevisionNo,
    String imageUrl,
    int imageWidth,
    int imageHeight
) {
}
