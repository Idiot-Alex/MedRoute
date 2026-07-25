package com.medroute.nav.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum EdgeDirection {
    FORWARD("forward"),
    BOTH("both");

    private final String apiValue;

    EdgeDirection(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonCreator
    public static EdgeDirection from(String value) {
        if (value == null || value.isBlank()) {
            return BOTH;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "forward" -> FORWARD;
            case "both", "bidirectional" -> BOTH;
            default -> throw new IllegalArgumentException("Unknown edge direction: " + value);
        };
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }
}
