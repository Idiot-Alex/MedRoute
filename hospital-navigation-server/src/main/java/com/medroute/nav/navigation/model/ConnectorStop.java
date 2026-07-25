package com.medroute.nav.navigation.model;

import java.util.UUID;

public record ConnectorStop(
    UUID id,
    String code,
    UUID connectorId,
    UUID floorId,
    UUID nodeId
) {
}
