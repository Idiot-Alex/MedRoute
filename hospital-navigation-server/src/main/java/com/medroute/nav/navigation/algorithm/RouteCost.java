package com.medroute.nav.navigation.algorithm;

import java.math.BigDecimal;
import java.util.Objects;

public record RouteCost(long timeSeconds, BigDecimal distanceMeters)
    implements Comparable<RouteCost> {

    public static final RouteCost ZERO = new RouteCost(0, BigDecimal.ZERO);

    public RouteCost {
        distanceMeters = Objects.requireNonNull(distanceMeters, "distanceMeters");
        if (timeSeconds < 0 || distanceMeters.signum() < 0) {
            throw new IllegalArgumentException("Route cost must be non-negative");
        }
    }

    public RouteCost plus(int additionalSeconds, BigDecimal additionalMeters) {
        return new RouteCost(
            Math.addExact(timeSeconds, additionalSeconds),
            distanceMeters.add(additionalMeters)
        );
    }

    @Override
    public int compareTo(RouteCost other) {
        int timeComparison = Long.compare(timeSeconds, other.timeSeconds);
        if (timeComparison != 0) {
            return timeComparison;
        }
        return distanceMeters.compareTo(other.distanceMeters);
    }
}
