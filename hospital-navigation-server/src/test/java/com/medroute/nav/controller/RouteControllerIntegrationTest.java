package com.medroute.nav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.medroute.nav.dto.NavigationRouteRequest;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.model.GraphNode;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.service.InMemoryOperationStatusService;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import com.medroute.nav.navigation.service.MultiFloorRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.BUILDING_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.ELEVATOR_A_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.ELEVATOR_B_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.POI_ENTRANCE_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.POI_ULTRASOUND_3F_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.RELEASE_ID;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RouteControllerIntegrationTest {
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    private InMemoryOperationStatusService operationStatus;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        operationStatus = new InMemoryOperationStatusService();

        MultiFloorRouteService routeService = new MultiFloorRouteService(
            new InMemoryPublishedGraphService(),
            operationStatus,
            Clock.systemUTC()
        );

        mockMvc = createMockMvc(routeService);
    }

    private MockMvc createMockMvc(MultiFloorRouteService routeService) {
        return MockMvcBuilders
            .standaloneSetup(new RouteController(routeService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new RequestIdFilter())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(objectMapper)
            )
            .build();
    }

    @Test
    void returnsTheFormalMultiFloorContractAndEchoesRequestId() throws Exception {
        NavigationRouteRequest request = request(
            RELEASE_ID,
            RouteMode.ACCESSIBLE
        );

        mockMvc.perform(
                post("/api/routes")
                    .header(RequestIdFilter.HEADER_NAME, "req-stage1-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    RequestIdFilter.HEADER_NAME,
                    "req-stage1-test"
                )
            )
            .andExpect(jsonPath("$.releaseId").value(RELEASE_ID.toString()))
            .andExpect(jsonPath("$.buildingId").value(BUILDING_ID.toString()))
            .andExpect(jsonPath("$.routeMode").value("accessible"))
            .andExpect(jsonPath("$.summary.estimatedSeconds").value(125))
            .andExpect(jsonPath("$.summary.distanceMeters").value(75))
            .andExpect(jsonPath("$.segments.length()").value(2))
            .andExpect(jsonPath("$.transitions.length()").value(1))
            .andExpect(jsonPath("$.transitions[0].connectorCode").value("ELEV-A"))
            .andExpect(jsonPath("$.steps[0].type").value("start"))
            .andExpect(jsonPath("$.path").doesNotExist())
            .andExpect(jsonPath("$.estimatedTime").doesNotExist());
    }

    @Test
    void rejectsBlankRouteModeAsInvalidArgument() throws Exception {
        String body = """
            {
              "buildingId": "%s",
              "expectedReleaseId": "%s",
              "startPoiId": "%s",
              "endPoiId": "%s",
              "routeMode": ""
            }
            """.formatted(
            BUILDING_ID,
            RELEASE_ID,
            POI_ENTRANCE_ID,
            POI_ULTRASOUND_3F_ID
        );

        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                header().string(
                    RequestIdFilter.HEADER_NAME,
                    startsWith("req-")
                )
            )
            .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"))
            .andExpect(jsonPath("$.error.path").value("/api/routes"))
            .andExpect(jsonPath("$.error.requestId", startsWith("req-")));
    }

    @Test
    void returnsForbiddenForAnUnprivilegedStaffRoute() throws Exception {
        mockMvc.perform(
                post("/api/routes")
                    .header(RequestIdFilter.HEADER_NAME, "req-staff-test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            request(RELEASE_ID, RouteMode.STAFF)
                        )
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(
                header().string(
                    RequestIdFilter.HEADER_NAME,
                    "req-staff-test"
                )
            )
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(
                jsonPath("$.error.requestId").value("req-staff-test")
            );
    }

    @Test
    void returnsConflictWhenTheExpectedReleaseIsStale() throws Exception {
        UUID staleRelease = UUID.fromString(
            "00000000-0000-0000-0000-999999999999"
        );

        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            request(staleRelease, RouteMode.NORMAL)
                        )
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RELEASE_MISMATCH"))
            .andExpect(
                jsonPath("$.error.details[0].field")
                    .value("expectedReleaseId")
            );
    }

    @Test
    void returnsUnprocessableEntityInsteadOfAnEmptyRoute() throws Exception {
        operationStatus.closeConnector(ELEVATOR_A_ID);
        operationStatus.closeConnector(ELEVATOR_B_ID);

        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            request(RELEASE_ID, RouteMode.ACCESSIBLE)
                        )
                    )
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("ROUTE_UNREACHABLE"));
    }

    @Test
    void returnsNotFoundForAnUnknownBuilding() throws Exception {
        NavigationRouteRequest request = new NavigationRouteRequest(
            UUID.fromString("00000000-0000-0000-0000-999999999997"),
            RELEASE_ID,
            POI_ENTRANCE_ID,
            POI_ULTRASOUND_3F_ID,
            RouteMode.NORMAL
        );

        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsAStandardErrorForAnUnsupportedMethod() throws Exception {
        mockMvc.perform(get("/api/routes"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
            .andExpect(jsonPath("$.error.requestId", startsWith("req-")));
    }

    @Test
    void returnsAStandardErrorForAnUnsupportedMediaType() throws Exception {
        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{}")
            )
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(
                jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE")
            )
            .andExpect(jsonPath("$.error.requestId", startsWith("req-")));
    }

    @Test
    void returnsAStandardInternalErrorForABrokenPublishedGraph()
        throws Exception {
        NavigationGraph base = InMemoryPublishedGraphService.buildGraph();
        Map<UUID, GraphNode> nodes = new LinkedHashMap<>(base.nodes());
        nodes.remove(base.pois().get(POI_ENTRANCE_ID).nodeId());
        NavigationGraph brokenGraph = new NavigationGraph(
            base.releaseId(),
            base.buildingId(),
            base.floors(),
            nodes,
            base.outgoing(),
            base.pois(),
            base.connectors(),
            base.connectorStops(),
            base.verticalLinks()
        );
        MultiFloorRouteService brokenRouteService = new MultiFloorRouteService(
            new InMemoryPublishedGraphService(brokenGraph),
            new InMemoryOperationStatusService(),
            Clock.systemUTC()
        );
        mockMvc = createMockMvc(brokenRouteService);

        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            request(RELEASE_ID, RouteMode.NORMAL)
                        )
                    )
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
            .andExpect(
                jsonPath("$.error.message")
                    .value("服务暂时不可用，请稍后重试。")
            );
    }

    private NavigationRouteRequest request(
        UUID expectedReleaseId,
        RouteMode routeMode
    ) {
        return new NavigationRouteRequest(
            BUILDING_ID,
            expectedReleaseId,
            POI_ENTRANCE_ID,
            POI_ULTRASOUND_3F_ID,
            routeMode
        );
    }
}
