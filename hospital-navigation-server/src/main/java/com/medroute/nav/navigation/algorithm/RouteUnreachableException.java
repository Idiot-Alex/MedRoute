package com.medroute.nav.navigation.algorithm;

import java.util.UUID;

public class RouteUnreachableException extends RuntimeException {
    public RouteUnreachableException(UUID startNodeId, UUID endNodeId) {
        super("No route found for nodes " + startNodeId + " -> " + endNodeId);
    }
}
