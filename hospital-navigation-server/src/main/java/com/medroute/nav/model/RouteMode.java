package com.medroute.nav.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum RouteMode {
    NORMAL,
    ACCESSIBLE,
    LESS_ELEVATOR,
    STAFF;

    public static RouteMode from(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "normal" -> NORMAL;
            case "accessible" -> ACCESSIBLE;
            case "less_elevator" -> LESS_ELEVATOR;
            case "staff" -> STAFF;
            default -> throw new IllegalArgumentException("Unknown routeMode: " + value);
        };
    }

    @JsonCreator
    public static RouteMode fromJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("routeMode is required");
        }
        return from(value);
    }

    public boolean supported() {
        return this == NORMAL || this == ACCESSIBLE;
    }

    @JsonValue
    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
