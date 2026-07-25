package com.medroute.nav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.CreateDraftRequest;
import com.medroute.nav.dto.CreateOperationClosureRequest;
import com.medroute.nav.dto.DraftGraphPayload;
import com.medroute.nav.dto.PublishReleaseRequest;
import com.medroute.nav.navigation.service.InMemoryPublishedGraphService;
import com.medroute.nav.navigation.service.MapAuthoringService;
import com.medroute.nav.navigation.service.OperationManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class MapAuthoringControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MapAuthoringService authoringService;

    @Autowired
    private OperationManagementService operationManagementService;

    @Test
    void copiesEditsValidatesPublishesAndChangesTheLiveRoute()
        throws Exception {
        MvcResult created = mockMvc.perform(
                post(
                    "/api/admin/buildings/{buildingId}/releases/drafts",
                    InMemoryPublishedGraphService.BUILDING_ID
                )
                    .header(
                        MapAuthoringController.ADMIN_USER_HEADER,
                        "integration-test"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateDraftRequest(
                                "DRAFT-INTEGRATION-" + shortId(),
                                InMemoryPublishedGraphService.RELEASE_ID,
                                "验证草稿发布闭环"
                            )
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("ETag", "\"0\""))
            .andExpect(jsonPath("$.release.status").value("draft"))
            .andExpect(jsonPath("$.graph.nodes.length()").value(15))
            .andReturn();

        AdminWorkspaceResponse workspace = objectMapper.readValue(
            created.getResponse().getContentAsByteArray(),
            AdminWorkspaceResponse.class
        );
        DraftGraphPayload changedGraph = withElevatorANotAccessible(
            workspace.graph()
        );

        MvcResult saved = mockMvc.perform(
                put(
                    "/api/admin/releases/{releaseId}/workspace",
                    workspace.release().id()
                )
                    .header("If-Match", "\"0\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(changedGraph))
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"1\""))
            .andExpect(jsonPath("$.release.contentRevision").value(1))
            .andReturn();
        AdminWorkspaceResponse savedWorkspace = objectMapper.readValue(
            saved.getResponse().getContentAsByteArray(),
            AdminWorkspaceResponse.class
        );

        mockMvc.perform(
                post(
                    "/api/admin/releases/{releaseId}/validate",
                    savedWorkspace.release().id()
                )
                    .header("If-Match", "\"1\"")
                    .header(
                        MapAuthoringController.ADMIN_USER_HEADER,
                        "integration-test"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.errors.length()").value(0));

        mockMvc.perform(
                post(
                    "/api/admin/releases/{releaseId}/publish",
                    savedWorkspace.release().id()
                )
                    .header("If-Match", "\"1\"")
                    .header(
                        MapAuthoringController.ADMIN_USER_HEADER,
                        "integration-test"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "reason": "集成测试完成，切换发布版本。"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.release.id")
                    .value(savedWorkspace.release().id().toString())
            );

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
                            savedWorkspace.release().id(),
                            InMemoryPublishedGraphService.POI_ENTRANCE_ID,
                            InMemoryPublishedGraphService.POI_ULTRASOUND_3F_ID
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.estimatedSeconds").value(145))
            .andExpect(jsonPath("$.transitions.length()").value(2))
            .andExpect(
                jsonPath("$.transitions[0].connectorCode").value("ELEV-B")
            )
            .andExpect(
                jsonPath("$.transitions[1].connectorCode").value("ELEV-B")
            );
    }

    @Test
    void reportsAnUnlinkedConnectorStopBeforePublish() {
        AdminWorkspaceResponse draft = authoringService.createDraft(
            InMemoryPublishedGraphService.BUILDING_ID,
            new CreateDraftRequest(
                "DRAFT-INVALID-" + shortId(),
                InMemoryPublishedGraphService.RELEASE_ID,
                "移除 A 电梯跨层连接"
            ),
            "integration-test"
        );
        List<DraftGraphPayload.VerticalLink> links = draft.graph()
            .verticalLinks()
            .stream()
            .filter(link ->
                !link.connectorId().equals(
                    InMemoryPublishedGraphService.ELEVATOR_A_ID
                )
            )
            .toList();
        DraftGraphPayload graph = new DraftGraphPayload(
            draft.graph().nodes(),
            draft.graph().edges(),
            draft.graph().pois(),
            draft.graph().connectors(),
            draft.graph().connectorStops(),
            links
        );
        AdminWorkspaceResponse saved = authoringService.saveGraph(
            draft.release().id(),
            0,
            graph
        );

        AdminValidationResponse validation = authoringService.validate(
            saved.release().id(),
            1,
            "integration-test"
        );

        assertThat(validation.passed()).isFalse();
        assertThat(validation.errors())
            .extracting(AdminValidationResponse.Issue::code)
            .contains("CONNECTOR_STOP_NOT_LINKED");
    }

    @Test
    void discardsADraftWithoutChangingThePublishedRelease() throws Exception {
        AdminWorkspaceResponse draft = authoringService.createDraft(
            InMemoryPublishedGraphService.BUILDING_ID,
            new CreateDraftRequest(
                "DRAFT-DISCARD-" + shortId(),
                InMemoryPublishedGraphService.RELEASE_ID,
                "验证放弃草稿"
            ),
            "integration-test"
        );

        mockMvc.perform(
                delete(
                    "/api/admin/releases/{releaseId}",
                    draft.release().id()
                )
                    .header("If-Match", "\"0\"")
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get(
                    "/api/admin/releases/{releaseId}",
                    draft.release().id()
                )
            )
            .andExpect(status().isNotFound());
        mockMvc.perform(
                get(
                    "/api/buildings/{buildingId}/navigation-context",
                    InMemoryPublishedGraphService.BUILDING_ID
                )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.release.id")
                    .value(InMemoryPublishedGraphService.RELEASE_ID.toString())
            );
    }

    @Test
    void uploadsAVersionedFloorMapAndScalesExistingAnnotations()
        throws Exception {
        AdminWorkspaceResponse draft = authoringService.createDraft(
            InMemoryPublishedGraphService.BUILDING_ID,
            new CreateDraftRequest(
                "DRAFT-MAP-" + shortId(),
                InMemoryPublishedGraphService.RELEASE_ID,
                "替换 1F 底图"
            ),
            "integration-test"
        );
        byte[] mapImage = png(500, 400);

        MvcResult uploaded = mockMvc.perform(
                multipart(
                    "/api/admin/releases/{releaseId}/floors/{floorId}/map",
                    draft.release().id(),
                    InMemoryPublishedGraphService.FLOOR_1_ID
                )
                    .file(
                        new MockMultipartFile(
                            "file",
                            "1f.png",
                            MediaType.IMAGE_PNG_VALUE,
                            mapImage
                        )
                    )
                    .header("If-Match", "\"0\"")
                    .header(
                        MapAuthoringController.ADMIN_USER_HEADER,
                        "integration-test"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"1\""))
            .andExpect(jsonPath("$.release.contentRevision").value(1))
            .andExpect(
                jsonPath("$.floors[0].mapRevision.revisionNo").value(2)
            )
            .andExpect(
                jsonPath("$.floors[0].mapRevision.imageWidth").value(500)
            )
            .andExpect(
                jsonPath("$.floors[0].mapRevision.imageHeight").value(400)
            )
            .andReturn();

        AdminWorkspaceResponse workspace = objectMapper.readValue(
            uploaded.getResponse().getContentAsByteArray(),
            AdminWorkspaceResponse.class
        );
        DraftGraphPayload.Poi entrance = workspace.graph().pois().stream()
            .filter(poi ->
                poi.id().equals(InMemoryPublishedGraphService.POI_ENTRANCE_ID)
            )
            .findFirst()
            .orElseThrow();
        assertThat(entrance.x()).isEqualTo(250);
        assertThat(entrance.y()).isEqualTo(387.5);

        String imageUrl = workspace.floors().get(0)
            .mapRevision()
            .imageUrl();
        mockMvc.perform(get(imageUrl))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(content().bytes(mapImage))
            .andExpect(header().exists("ETag"))
            .andExpect(header().exists("Cache-Control"));
    }

    @Test
    void carriesAnActiveConnectorClosureToTheNewPublishedRelease() {
        operationManagementService.create(
            InMemoryPublishedGraphService.BUILDING_ID,
            new CreateOperationClosureRequest(
                "vertical_connector",
                InMemoryPublishedGraphService.ELEVATOR_A_ID,
                null,
                null,
                "A 电梯持续检修"
            ),
            "integration-test"
        );
        AdminWorkspaceResponse draft = authoringService.createDraft(
            InMemoryPublishedGraphService.BUILDING_ID,
            new CreateDraftRequest(
                "DRAFT-CARRY-" + shortId(),
                InMemoryPublishedGraphService.RELEASE_ID,
                "验证运营封闭状态随发布版本迁移"
            ),
            "integration-test"
        );
        AdminValidationResponse validation = authoringService.validate(
            draft.release().id(),
            0,
            "integration-test"
        );
        assertThat(validation.passed()).isTrue();

        authoringService.publish(
            draft.release().id(),
            0,
            new PublishReleaseRequest("保持 A 电梯检修状态并发布"),
            "integration-test"
        );

        var closures = operationManagementService.list(
            InMemoryPublishedGraphService.BUILDING_ID
        );
        assertThat(closures.releaseId()).isEqualTo(draft.release().id());
        assertThat(closures.items()).hasSize(1);
        assertThat(closures.items().get(0).targetId())
            .isEqualTo(InMemoryPublishedGraphService.ELEVATOR_A_ID);

        authoringService.rollback(
            InMemoryPublishedGraphService.RELEASE_ID,
            new PublishReleaseRequest("回滚并保持 A 电梯检修状态"),
            "integration-test"
        );
        var rolledBackClosures = operationManagementService.list(
            InMemoryPublishedGraphService.BUILDING_ID
        );
        assertThat(rolledBackClosures.releaseId())
            .isEqualTo(InMemoryPublishedGraphService.RELEASE_ID);
        assertThat(rolledBackClosures.items()).hasSize(1);
        assertThat(rolledBackClosures.items().get(0).targetId())
            .isEqualTo(InMemoryPublishedGraphService.ELEVATOR_A_ID);
    }

    private DraftGraphPayload withElevatorANotAccessible(
        DraftGraphPayload graph
    ) {
        List<DraftGraphPayload.Connector> connectors = graph.connectors()
            .stream()
            .map(connector ->
                connector.id().equals(
                    InMemoryPublishedGraphService.ELEVATOR_A_ID
                )
                    ? new DraftGraphPayload.Connector(
                        connector.id(),
                        connector.code(),
                        connector.name(),
                        connector.type(),
                        connector.accessScope(),
                        false,
                        connector.enabled()
                    )
                    : connector
            )
            .toList();
        return new DraftGraphPayload(
            graph.nodes(),
            graph.edges(),
            graph.pois(),
            connectors,
            graph.connectorStops(),
            graph.verticalLinks()
        );
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(
            width,
            height,
            BufferedImage.TYPE_INT_RGB
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
