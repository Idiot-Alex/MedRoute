package com.medroute.nav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medroute.nav.dto.NavigationRouteRequest;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.model.PoiSnapshot;
import com.medroute.nav.navigation.service.InMemoryOperationStatusService;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import com.medroute.nav.navigation.service.MultiFloorRouteService;
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
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.POI_ENTRANCE_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.POI_ULTRASOUND_3F_ID;
import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.RELEASE_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessibleRouteEndpointControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules();

    @Test
    void rejectsAnInaccessibleStartPoiWithAFieldLevel422() throws Exception {
        assertInaccessibleEndpointRejected(
            POI_ENTRANCE_ID,
            POI_ENTRANCE_ID,
            POI_ULTRASOUND_3F_ID,
            "startPoiId"
        );
    }

    @Test
    void rejectsAnInaccessibleEndPoiWithAFieldLevel422() throws Exception {
        assertInaccessibleEndpointRejected(
            POI_ULTRASOUND_3F_ID,
            POI_ENTRANCE_ID,
            POI_ULTRASOUND_3F_ID,
            "endPoiId"
        );
    }

    private void assertInaccessibleEndpointRejected(
        UUID inaccessiblePoiId,
        UUID startPoiId,
        UUID endPoiId,
        String expectedField
    ) throws Exception {
        MockMvc mockMvc = mockMvcWithInaccessiblePoi(inaccessiblePoiId);
        NavigationRouteRequest request = new NavigationRouteRequest(
            BUILDING_ID,
            RELEASE_ID,
            startPoiId,
            endPoiId,
            RouteMode.ACCESSIBLE
        );

        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(
                jsonPath("$.error.code").value("ENDPOINT_NOT_ACCESSIBLE")
            )
            .andExpect(
                jsonPath("$.error.details[0].field").value(expectedField)
            )
            .andExpect(
                jsonPath("$.error.details[0].reason")
                    .value("POI " + inaccessiblePoiId + " is not accessible")
            );
    }

    private MockMvc mockMvcWithInaccessiblePoi(UUID poiId) {
        NavigationGraph base = InMemoryPublishedGraphService.buildGraph();
        Map<UUID, PoiSnapshot> pois = new LinkedHashMap<>(base.pois());
        PoiSnapshot poi = pois.get(poiId);
        pois.put(
            poiId,
            new PoiSnapshot(
                poi.id(),
                poi.code(),
                poi.name(),
                poi.category(),
                poi.floorId(),
                poi.nodeId(),
                poi.x(),
                poi.y(),
                false,
                poi.searchKeywords()
            )
        );
        NavigationGraph graph = new NavigationGraph(
            base.releaseId(),
            base.buildingId(),
            base.floors(),
            base.nodes(),
            base.outgoing(),
            pois,
            base.connectors(),
            base.connectorStops(),
            base.verticalLinks()
        );
        MultiFloorRouteService routeService = new MultiFloorRouteService(
            new InMemoryPublishedGraphService(graph),
            new InMemoryOperationStatusService(),
            Clock.systemUTC()
        );
        return MockMvcBuilders
            .standaloneSetup(new RouteController(routeService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new RequestIdFilter())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(objectMapper)
            )
            .build();
    }
}
