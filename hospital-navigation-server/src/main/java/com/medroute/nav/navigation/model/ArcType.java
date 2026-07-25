package com.medroute.nav.navigation.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ArcType {
    WALK,
    ELEVATOR,
    STAIRS;

    @JsonValue
    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean vertical() {
        return this == ELEVATOR || this == STAIRS;
    }
}
