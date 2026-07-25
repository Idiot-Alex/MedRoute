package com.medroute.nav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import com.medroute.nav.navigation.service.NavigationContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.medroute.nav.navigation.service.InMemoryPublishedGraphService.BUILDING_ID;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NavigationContextControllerIntegrationTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        NavigationContextService contextService = new NavigationContextService(
            new InMemoryPublishedGraphService()
        );
        mockMvc = MockMvcBuilders
            .standaloneSetup(new NavigationContextController(contextService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new RequestIdFilter())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(objectMapper)
            )
            .build();
    }

    @Test
    void returnsPublishedFloorsForTheMapRenderer() throws Exception {
        mockMvc.perform(
                get("/api/buildings/{buildingId}/navigation-context", BUILDING_ID)
                    .header(RequestIdFilter.HEADER_NAME, "req-context-test")
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    RequestIdFilter.HEADER_NAME,
                    "req-context-test"
                )
            )
            .andExpect(jsonPath("$.building.id").value(BUILDING_ID.toString()))
            .andExpect(jsonPath("$.release.code").value("REL-TEST-001"))
            .andExpect(jsonPath("$.floors.length()").value(3))
            .andExpect(jsonPath("$.floors[0].code").value("1F"))
            .andExpect(
                jsonPath("$.floors[0].mapRevision.imageUrl")
                    .value("/hospital-map-demo/assets/hndfsrmyy/outpatient-1f.jpg")
            )
            .andExpect(
                jsonPath("$.floors[0].mapRevision.imageWidth").value(1000)
            )
            .andExpect(
                jsonPath("$.floors[0].mapRevision.imageHeight").value(800)
            )
            .andExpect(
                jsonPath("$.supportedRouteModes[1]").value("accessible")
            );
    }

    @Test
    void returnsCurrentReleasePoisForTheMapRenderer() throws Exception {
        mockMvc.perform(
                get("/api/buildings/{buildingId}/pois", BUILDING_ID)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.items[3].name").value("超声医学科"))
            .andExpect(jsonPath("$.nextPageToken").doesNotExist());
    }

    @Test
    void returnsAStandardErrorForAnUnknownBuilding() throws Exception {
        mockMvc.perform(
                get(
                    "/api/buildings/{buildingId}/navigation-context",
                    "00000000-0000-0000-0000-999999999997"
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(
                jsonPath("$.error.requestId", startsWith("req-"))
            );
    }
}
