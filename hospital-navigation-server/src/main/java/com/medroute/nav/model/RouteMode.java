package com.medroute.nav.model;

public enum RouteMode {
    NORMAL,
    ACCESSIBLE,
    LESS_ELEVATOR,
    STAFF;

    public static RouteMode from(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        return switch (value.trim().toLowerCase()) {
            case "accessible" -> ACCESSIBLE;
            case "less_elevator" -> LESS_ELEVATOR;
            case "staff" -> STAFF;
            default -> NORMAL;
        };
    }

    public String apiValue() {
        return name().toLowerCase();
    }
}
