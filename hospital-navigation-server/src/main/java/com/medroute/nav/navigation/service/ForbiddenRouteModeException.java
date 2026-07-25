package com.medroute.nav.navigation.service;

public class ForbiddenRouteModeException extends RuntimeException {
    public ForbiddenRouteModeException(String routeMode) {
        super("Route mode requires additional permission: " + routeMode);
    }
}
