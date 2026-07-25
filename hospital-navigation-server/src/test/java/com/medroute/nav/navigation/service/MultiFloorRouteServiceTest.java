package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.NavigationRouteRequest;
import com.medroute.nav.dto.NavigationRouteResponse;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.algorithm.RouteUnreachableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.BUILDING_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.ELEVATOR_A_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.ELEVATOR_B_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.FLOOR_1_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.FLOOR_2_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.FLOOR_3_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.POI_CLINIC_2F_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.POI_ENTRANCE_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.POI_PHARMACY_3F_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.RELEASE_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.STAIRS_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiFloorRouteServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-25T08:00:00Z");

    private InMemoryOperationStatusService operationStatus;
    private MultiFloorRouteService routeService;

    @BeforeEach
    void setUp() {
        operationStatus = new InMemoryOperationStatusService();
        routeService = new MultiFloorRouteService(
            new InMemoryPublishedGraphService(),
            operationStatus,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void normalRouteUsesTheFasterAdjacentFloorStairs() {
        NavigationRouteResponse response = route(
            POI_ENTRANCE_ID,
            POI_PHARMACY_3F_ID,
            RouteMode.NORMAL
        );

        assertEquals(RELEASE_ID, response.releaseId());
        assertEquals(BUILDING_ID, response.buildingId());
        assertEquals(NOW, response.calculatedAt());
        assertEquals(List.of("1F", "2F", "3F"), floorCodes(response));
        assertEquals(2, response.transitions().size());
        assertTrue(
            response.transitions().stream()
                .allMatch(transition -> transition.connectorId().equals(STAIRS_ID))
        );
        assertEquals(97, response.summary().estimatedSeconds());
        assertEquals(new BigDecimal("61"), response.summary().distanceMeters());
        assertSummaryMatchesComponents(response);
    }

    @Test
    void accessibleRouteUsesElevatorAWithoutInventingASecondFloorStop() {
        NavigationRouteResponse response = route(
            POI_ENTRANCE_ID,
            POI_PHARMACY_3F_ID,
            RouteMode.ACCESSIBLE
        );

        assertEquals(List.of("1F", "3F"), floorCodes(response));
        assertEquals(1, response.transitions().size());
        NavigationRouteResponse.RouteTransition transition =
            response.transitions().get(0);
        assertEquals(ELEVATOR_A_ID, transition.connectorId());
        assertEquals(FLOOR_1_ID, transition.fromFloorId());
        assertEquals(FLOOR_3_ID, transition.toFloorId());
        assertFalse(
            response.segments().stream()
                .anyMatch(segment -> segment.floorId().equals(FLOOR_2_ID))
        );
        assertEquals(125, response.summary().estimatedSeconds());
        assertEquals(new BigDecimal("75"), response.summary().distanceMeters());
        assertSummaryMatchesComponents(response);
    }

    @Test
    void generatesDecisionPointStepsWithoutExposingInternalNodeCodes() {
        NavigationRouteResponse response = route(
            POI_ENTRANCE_ID,
            POI_PHARMACY_3F_ID,
            RouteMode.ACCESSIBLE
        );

        assertEquals(
            List.of(
                "start",
                "approach",
                "transition",
                "continue",
                "turn",
                "arrive"
            ),
            response.steps().stream()
                .map(NavigationRouteResponse.RouteStep::type)
                .toList()
        );
        assertEquals(
            "乘 A 电梯至 3F。",
            response.transitions().get(0).instruction()
        );
        assertEquals("前往 A 电梯。", response.steps().get(1).instruction());
        assertTrue(
            response.steps().stream()
                .map(NavigationRouteResponse.RouteStep::instruction)
                .noneMatch(instruction -> instruction.contains("N-"))
        );
        for (int index = 0; index < response.steps().size(); index++) {
            assertEquals(index + 1, response.steps().get(index).sequence());
        }
    }

    @Test
    void keepsAStraightSameFloorRouteConcise() {
        NavigationRouteResponse response = route(
            POI_ENTRANCE_ID,
            InMemoryPublishedGraphService.POI_REGISTRATION_ID,
            RouteMode.NORMAL
        );

        assertEquals(List.of("1F"), floorCodes(response));
        assertTrue(response.transitions().isEmpty());
        assertEquals(
            List.of("start", "arrive"),
            response.steps().stream()
                .map(NavigationRouteResponse.RouteStep::type)
                .toList()
        );
        assertEquals(30, response.summary().estimatedSeconds());
    }

    @Test
    void closingElevatorAReroutesAccessibleNavigationThroughElevatorB() {
        operationStatus.closeConnector(ELEVATOR_A_ID);

        NavigationRouteResponse response = route(
            POI_ENTRANCE_ID,
            POI_PHARMACY_3F_ID,
            RouteMode.ACCESSIBLE
        );

        assertEquals(List.of("1F", "2F", "3F"), floorCodes(response));
        assertEquals(2, response.transitions().size());
        assertTrue(
            response.transitions().stream()
                .allMatch(transition -> transition.connectorId().equals(ELEVATOR_B_ID))
        );
        assertEquals(145, response.summary().estimatedSeconds());
        assertSummaryMatchesComponents(response);
    }

    @Test
    void elevatorADoesNotProvideASecondFloorRoute() {
        NavigationRouteResponse response = route(
            POI_ENTRANCE_ID,
            POI_CLINIC_2F_ID,
            RouteMode.ACCESSIBLE
        );

        assertEquals(1, response.transitions().size());
        assertEquals(ELEVATOR_B_ID, response.transitions().get(0).connectorId());
        assertEquals(FLOOR_2_ID, response.transitions().get(0).toFloorId());
        assertEquals(115, response.summary().estimatedSeconds());
    }

    @Test
    void closingEveryAccessibleConnectorMakesTheRouteUnreachable() {
        operationStatus.closeConnector(ELEVATOR_A_ID);
        operationStatus.closeConnector(ELEVATOR_B_ID);

        assertThrows(
            RouteUnreachableException.class,
            () -> route(
                POI_ENTRANCE_ID,
                POI_PHARMACY_3F_ID,
                RouteMode.ACCESSIBLE
            )
        );
    }

    @Test
    void rejectsAStaleExpectedRelease() {
        NavigationRouteRequest request = new NavigationRouteRequest(
            BUILDING_ID,
            UUID.fromString("00000000-0000-0000-0000-999999999999"),
            POI_ENTRANCE_ID,
            POI_PHARMACY_3F_ID,
            RouteMode.ACCESSIBLE
        );

        assertThrows(
            ReleaseMismatchException.class,
            () -> routeService.calculateRoute(request)
        );
    }

    @Test
    void rejectsPoisOutsideTheActiveRelease() {
        NavigationRouteRequest request = new NavigationRouteRequest(
            BUILDING_ID,
            RELEASE_ID,
            POI_ENTRANCE_ID,
            UUID.fromString("00000000-0000-0000-0000-999999999998"),
            RouteMode.NORMAL
        );

        assertThrows(
            PoiNotInReleaseException.class,
            () -> routeService.calculateRoute(request)
        );
    }

    private NavigationRouteResponse route(
        UUID startPoiId,
        UUID endPoiId,
        RouteMode mode
    ) {
        return routeService.calculateRoute(
            new NavigationRouteRequest(
                BUILDING_ID,
                RELEASE_ID,
                startPoiId,
                endPoiId,
                mode
            )
        );
    }

    private List<String> floorCodes(NavigationRouteResponse response) {
        return response.segments().stream()
            .map(NavigationRouteResponse.RouteSegment::floorCode)
            .toList();
    }

    private void assertSummaryMatchesComponents(
        NavigationRouteResponse response
    ) {
        long componentSeconds = response.segments().stream()
            .mapToLong(NavigationRouteResponse.RouteSegment::estimatedSeconds)
            .sum()
            + response.transitions().stream()
                .mapToLong(NavigationRouteResponse.RouteTransition::estimatedSeconds)
                .sum();
        BigDecimal componentDistance = response.segments().stream()
            .map(NavigationRouteResponse.RouteSegment::distanceMeters)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(response.summary().estimatedSeconds(), componentSeconds);
        assertEquals(response.summary().distanceMeters(), componentDistance);
    }
}
