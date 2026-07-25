package com.medroute.nav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medroute.nav.dto.OperationClosureListResponse;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class OperationManagementControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void closesAndReopensAConnectorWithoutPublishingAMapRelease()
        throws Exception {
        MvcResult closed = mockMvc.perform(
                post(
                    "/api/admin/buildings/{buildingId}/operations/closures",
                    InMemoryPublishedGraphService.BUILDING_ID
                )
                    .header(
                        MapAuthoringController.ADMIN_USER_HEADER,
                        "integration-test"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "targetType": "vertical_connector",
                          "targetId": "%s",
                          "reason": "A 电梯临时检修"
                        }
                        """.formatted(
                            InMemoryPublishedGraphService.ELEVATOR_A_ID
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(
                jsonPath("$.items[0].targetType")
                    .value("vertical_connector")
            )
            .andExpect(
                jsonPath("$.items[0].targetCode").value("ELEV-A")
            )
            .andReturn();
        OperationClosureListResponse response = objectMapper.readValue(
            closed.getResponse().getContentAsByteArray(),
            OperationClosureListResponse.class
        );

        expectAccessibleRouteTime(145);

        mockMvc.perform(
                delete(
                    "/api/admin/operations/closures/{closureId}",
                    response.items().get(0).id()
                )
                    .header(
                        MapAuthoringController.ADMIN_USER_HEADER,
                        "integration-test"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        expectAccessibleRouteTime(125);
    }

    private void expectAccessibleRouteTime(int seconds) throws Exception {
        mockMvc.perform(
                post("/api/routes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "buildingId": "%s",
                          "expectedReleaseId": "%s",
                          "startPoiId": "%s",
                          "endPoiId": "%s",
                          "routeMode": "accessible"
                        }
                        """.formatted(
                            InMemoryPublishedGraphService.BUILDING_ID,
                            InMemoryPublishedGraphService.RELEASE_ID,
                            InMemoryPublishedGraphService.POI_ENTRANCE_ID,
                            InMemoryPublishedGraphService.POI_ULTRASOUND_3F_ID
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.summary.estimatedSeconds").value(seconds)
            );
    }
}
