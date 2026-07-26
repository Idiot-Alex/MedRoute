package com.medroute.nav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medroute.nav.dto.AdminRouteRegressionCaseListResponse;
import com.medroute.nav.dto.AdminRouteRegressionCaseResponse;
import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.CreateDraftRequest;
import com.medroute.nav.dto.RouteRegressionCaseRequest;
import com.medroute.nav.model.RouteMode;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import com.medroute.nav.navigation.service.MapAuthoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class RouteRegressionControllerIntegrationTest {
    private static final UUID ULTRASOUND_NORMAL_CASE_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000007003"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MapAuthoringService authoringService;

    @Test
    void runsSeededCasesWithTheDraftValidation() throws Exception {
        mockMvc.perform(
                get(
                    "/api/admin/buildings/{buildingId}/route-regression-cases",
                    InMemoryPublishedGraphService.BUILDING_ID
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(12));

        AdminWorkspaceResponse draft = draft("DRAFT-REGRESSION-RUN-");
        MvcResult result = mockMvc.perform(
                post(
                    "/api/admin/releases/{releaseId}/validate",
                    draft.release().id()
                )
                    .header("If-Match", "\"0\"")
                    .header(
                        MapAuthoringController.ADMIN_USER_HEADER,
                        "regression-test"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.routeRegressions.length()").value(4))
            .andReturn();

        AdminValidationResponse validation = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            AdminValidationResponse.class
        );
        assertThat(validation.routeRegressions())
            .allMatch(regression -> regression.passed());
        assertThat(validation.routeRegressions())
            .filteredOn(regression ->
                regression.caseId().equals(ULTRASOUND_NORMAL_CASE_ID)
            )
            .singleElement()
            .satisfies(regression -> {
                assertThat(regression.estimatedSeconds()).isEqualTo(97L);
                assertThat(regression.distanceMeters())
                    .isEqualByComparingTo("61");
                assertThat(regression.connectorCodes())
                    .containsExactly("STAIRS-A");
            });
        assertThat(validation.routeRegressions())
            .filteredOn(regression ->
                regression.caseCode().equals(
                    "ENTRANCE-TO-ULTRASOUND-ACCESSIBLE"
                )
            )
            .singleElement()
            .satisfies(regression -> {
                assertThat(regression.estimatedSeconds()).isEqualTo(125L);
                assertThat(regression.connectorCodes())
                    .containsExactly("ELEV-A");
            });
    }

    @Test
    void invalidatesDraftValidationAndBlocksPublishWhenACriticalCaseFails()
        throws Exception {
        AdminWorkspaceResponse draft = draft("DRAFT-REGRESSION-BLOCK-");
        AdminValidationResponse initial = authoringService.validate(
            draft.release().id(),
            0,
            "regression-test"
        );
        assertThat(initial.passed()).isTrue();

        AdminRouteRegressionCaseResponse existing = cases().items().stream()
            .filter(item -> item.id().equals(ULTRASOUND_NORMAL_CASE_ID))
            .findFirst()
            .orElseThrow();
        RouteRegressionCaseRequest tooStrict = new RouteRegressionCaseRequest(
            existing.code(),
            existing.name(),
            existing.startPoiCode(),
            existing.endPoiCode(),
            existing.routeMode(),
            existing.critical(),
            existing.enabled(),
            existing.maxDistanceMeters(),
            1
        );
        mockMvc.perform(
                put(
                    "/api/admin/route-regression-cases/{caseId}",
                    existing.id()
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(tooStrict))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxEstimatedSeconds").value(1));

        publish(draft)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        MvcResult result = mockMvc.perform(
                post(
                    "/api/admin/releases/{releaseId}/validate",
                    draft.release().id()
                )
                    .header("If-Match", "\"0\"")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(false))
            .andReturn();
        AdminValidationResponse failed = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            AdminValidationResponse.class
        );
        assertThat(failed.errors())
            .extracting(AdminValidationResponse.Issue::code)
            .contains("ROUTE_REGRESSION_LIMIT_EXCEEDED");
        assertThat(failed.routeRegressions())
            .filteredOn(regression ->
                regression.caseId().equals(ULTRASOUND_NORMAL_CASE_ID)
            )
            .singleElement()
            .satisfies(regression -> {
                assertThat(regression.passed()).isFalse();
                assertThat(regression.estimatedSeconds()).isEqualTo(97L);
            });

        publish(draft)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createsUpdatesAndDeletesACase() throws Exception {
        RouteRegressionCaseRequest request = new RouteRegressionCaseRequest(
            "ENTRANCE-TO-LAB-OPTIONAL-" + shortId(),
            "入口到检验科提醒路线",
            "P-ENTRANCE",
            "P-LAB-2F",
            RouteMode.NORMAL,
            false,
            true,
            new BigDecimal("180"),
            300
        );
        MvcResult created = mockMvc.perform(
                post(
                    "/api/admin/buildings/{buildingId}/route-regression-cases",
                    InMemoryPublishedGraphService.BUILDING_ID
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.critical").value(false))
            .andReturn();
        AdminRouteRegressionCaseResponse regressionCase =
            objectMapper.readValue(
                created.getResponse().getContentAsByteArray(),
                AdminRouteRegressionCaseResponse.class
            );

        RouteRegressionCaseRequest changed = new RouteRegressionCaseRequest(
            regressionCase.code(),
            "入口到检验科无障碍提醒路线",
            regressionCase.startPoiCode(),
            regressionCase.endPoiCode(),
            RouteMode.ACCESSIBLE,
            false,
            false,
            null,
            null
        );
        mockMvc.perform(
                put(
                    "/api/admin/route-regression-cases/{caseId}",
                    regressionCase.id()
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(changed))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.routeMode").value("accessible"))
            .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(
                delete(
                    "/api/admin/route-regression-cases/{caseId}",
                    regressionCase.id()
                )
            )
            .andExpect(status().isNoContent());
        assertThat(cases().items())
            .noneMatch(item -> item.id().equals(regressionCase.id()));
    }

    @Test
    void reportsANonCriticalFailureWithoutBlockingPublish() throws Exception {
        RouteRegressionCaseRequest request = new RouteRegressionCaseRequest(
            "OPTIONAL-TIME-LIMIT-" + shortId(),
            "非关键耗时提醒",
            "P-ENTRANCE",
            "P-REGISTRATION",
            RouteMode.NORMAL,
            false,
            true,
            null,
            1
        );
        mockMvc.perform(
                post(
                    "/api/admin/buildings/{buildingId}/route-regression-cases",
                    InMemoryPublishedGraphService.BUILDING_ID
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request))
            )
            .andExpect(status().isCreated());

        AdminWorkspaceResponse draft = draft("DRAFT-OPTIONAL-REGRESSION-");
        AdminValidationResponse validation = authoringService.validate(
            draft.release().id(),
            0,
            "regression-test"
        );

        assertThat(validation.passed()).isTrue();
        assertThat(validation.warnings())
            .extracting(AdminValidationResponse.Issue::code)
            .contains("ROUTE_REGRESSION_LIMIT_EXCEEDED");
        assertThat(validation.routeRegressions())
            .filteredOn(result -> result.caseName().equals("非关键耗时提醒"))
            .singleElement()
            .satisfies(result -> assertThat(result.passed()).isFalse());
    }

    private AdminWorkspaceResponse draft(String prefix) {
        return authoringService.createDraft(
            InMemoryPublishedGraphService.BUILDING_ID,
            new CreateDraftRequest(
                prefix + shortId(),
                InMemoryPublishedGraphService.RELEASE_ID,
                "关键路线回归集成测试"
            ),
            "regression-test"
        );
    }

    private AdminRouteRegressionCaseListResponse cases() throws Exception {
        MvcResult result = mockMvc.perform(
                get(
                    "/api/admin/buildings/{buildingId}/route-regression-cases",
                    InMemoryPublishedGraphService.BUILDING_ID
                )
            )
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            AdminRouteRegressionCaseListResponse.class
        );
    }

    private org.springframework.test.web.servlet.ResultActions publish(
        AdminWorkspaceResponse draft
    ) throws Exception {
        return mockMvc.perform(
            post(
                "/api/admin/releases/{releaseId}/publish",
                draft.release().id()
            )
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "验证关键路线失败时禁止发布。"
                    }
                    """
                )
        );
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
