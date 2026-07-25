package com.medroute.nav.controller;

import com.medroute.nav.dto.AdminReleaseListResponse;
import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.CreateDraftRequest;
import com.medroute.nav.dto.DraftGraphPayload;
import com.medroute.nav.dto.NavigationContextResponse;
import com.medroute.nav.dto.PublishReleaseRequest;
import com.medroute.nav.navigation.service.MapAuthoringService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@CrossOrigin(
    allowedHeaders = {
        "Content-Type",
        "If-Match",
        "X-Admin-User",
        "X-Request-Id"
    },
    exposedHeaders = {"ETag", "X-Request-Id"}
)
@RestController
@RequestMapping("/api/admin")
public class MapAuthoringController {
    public static final String ADMIN_USER_HEADER = "X-Admin-User";

    private final MapAuthoringService authoringService;

    public MapAuthoringController(MapAuthoringService authoringService) {
        this.authoringService = authoringService;
    }

    @GetMapping("/buildings/{buildingId}/releases")
    public AdminReleaseListResponse listReleases(
        @PathVariable UUID buildingId
    ) {
        return authoringService.listReleases(buildingId);
    }

    @PostMapping("/buildings/{buildingId}/releases/drafts")
    public ResponseEntity<AdminWorkspaceResponse> createDraft(
        @PathVariable UUID buildingId,
        @RequestBody CreateDraftRequest request,
        @RequestHeader(
            name = ADMIN_USER_HEADER,
            required = false
        ) String actor
    ) {
        AdminWorkspaceResponse workspace = authoringService.createDraft(
            buildingId,
            request,
            actor
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .eTag(etag(workspace.release().contentRevision()))
            .body(workspace);
    }

    @GetMapping("/releases/{releaseId}")
    public ResponseEntity<AdminWorkspaceResponse> workspace(
        @PathVariable UUID releaseId
    ) {
        AdminWorkspaceResponse workspace = authoringService.workspace(
            releaseId
        );
        return ResponseEntity
            .ok()
            .eTag(etag(workspace.release().contentRevision()))
            .body(workspace);
    }

    @DeleteMapping("/releases/{releaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(
        @PathVariable UUID releaseId,
        @RequestHeader("If-Match") String ifMatch
    ) {
        authoringService.deleteDraft(releaseId, revision(ifMatch));
    }

    @PutMapping("/releases/{releaseId}/workspace")
    public ResponseEntity<AdminWorkspaceResponse> saveWorkspace(
        @PathVariable UUID releaseId,
        @RequestHeader("If-Match") String ifMatch,
        @RequestBody DraftGraphPayload graph
    ) {
        AdminWorkspaceResponse workspace = authoringService.saveGraph(
            releaseId,
            revision(ifMatch),
            graph
        );
        return ResponseEntity
            .ok()
            .eTag(etag(workspace.release().contentRevision()))
            .body(workspace);
    }

    @PostMapping(
        value = "/releases/{releaseId}/floors/{floorId}/map",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AdminWorkspaceResponse> replaceFloorMap(
        @PathVariable UUID releaseId,
        @PathVariable UUID floorId,
        @RequestHeader("If-Match") String ifMatch,
        @RequestHeader(
            name = ADMIN_USER_HEADER,
            required = false
        ) String actor,
        @RequestPart("file") MultipartFile file
    ) {
        AdminWorkspaceResponse workspace = authoringService.replaceFloorMap(
            releaseId,
            floorId,
            revision(ifMatch),
            file,
            actor
        );
        return ResponseEntity
            .ok()
            .eTag(etag(workspace.release().contentRevision()))
            .body(workspace);
    }

    @PostMapping("/releases/{releaseId}/validate")
    public ResponseEntity<AdminValidationResponse> validate(
        @PathVariable UUID releaseId,
        @RequestHeader("If-Match") String ifMatch,
        @RequestHeader(
            name = ADMIN_USER_HEADER,
            required = false
        ) String actor
    ) {
        AdminValidationResponse validation = authoringService.validate(
            releaseId,
            revision(ifMatch),
            actor
        );
        return ResponseEntity
            .ok()
            .eTag(etag(validation.contentRevision()))
            .body(validation);
    }

    @PostMapping("/releases/{releaseId}/publish")
    public NavigationContextResponse publish(
        @PathVariable UUID releaseId,
        @RequestHeader("If-Match") String ifMatch,
        @RequestHeader(
            name = ADMIN_USER_HEADER,
            required = false
        ) String actor,
        @RequestBody PublishReleaseRequest request
    ) {
        return authoringService.publish(
            releaseId,
            revision(ifMatch),
            request,
            actor
        );
    }

    @PostMapping("/releases/{releaseId}/rollback")
    public NavigationContextResponse rollback(
        @PathVariable UUID releaseId,
        @RequestHeader(
            name = ADMIN_USER_HEADER,
            required = false
        ) String actor,
        @RequestBody PublishReleaseRequest request
    ) {
        return authoringService.rollback(releaseId, request, actor);
    }

    private long revision(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("If-Match header is required");
        }
        String candidate = value.trim();
        if (candidate.startsWith("W/")) {
            candidate = candidate.substring(2);
        }
        if (
            candidate.length() >= 2
                && candidate.startsWith("\"")
                && candidate.endsWith("\"")
        ) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        try {
            long revision = Long.parseLong(candidate);
            if (revision < 0) {
                throw new NumberFormatException("negative revision");
            }
            return revision;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                "If-Match must contain a numeric content revision"
            );
        }
    }

    private String etag(long revision) {
        return Long.toString(revision);
    }
}
