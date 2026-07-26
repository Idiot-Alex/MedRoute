package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminReleaseListResponse;
import com.medroute.nav.dto.AdminRouteRegressionResult;
import com.medroute.nav.dto.AdminValidationResponse;
import com.medroute.nav.dto.AdminWorkspaceResponse;
import com.medroute.nav.dto.CreateDraftRequest;
import com.medroute.nav.dto.DraftGraphPayload;
import com.medroute.nav.dto.NavigationContextResponse;
import com.medroute.nav.dto.PublishReleaseRequest;
import com.medroute.nav.navigation.repository.JdbcMapAuthoringRepository;
import com.medroute.nav.navigation.service.RouteRegressionService.RouteRegressionRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MapAuthoringService {
    private static final long MAX_MAP_BYTES = 15L * 1024 * 1024;
    private static final long MAX_MAP_PIXELS = 50_000_000L;

    private final JdbcMapAuthoringRepository repository;
    private final DraftGraphValidator validator;
    private final NavigationContextService contextService;
    private final RouteRegressionService regressionService;

    public MapAuthoringService(
        JdbcMapAuthoringRepository repository,
        DraftGraphValidator validator,
        NavigationContextService contextService,
        RouteRegressionService regressionService
    ) {
        this.repository = repository;
        this.validator = validator;
        this.contextService = contextService;
        this.regressionService = regressionService;
    }

    public AdminReleaseListResponse listReleases(UUID buildingId) {
        return new AdminReleaseListResponse(
            repository.listReleases(buildingId),
            null
        );
    }

    public AdminWorkspaceResponse workspace(UUID releaseId) {
        return repository.workspace(releaseId);
    }

    public AdminWorkspaceResponse createDraft(
        UUID buildingId,
        CreateDraftRequest request,
        String actor
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String code = request.code();
        if (code == null || code.isBlank() || code.trim().length() > 80) {
            throw new IllegalArgumentException(
                "code is required and must not exceed 80 characters"
            );
        }
        return repository.createDraft(buildingId, request, actor(actor));
    }

    public void deleteDraft(UUID releaseId, long expectedRevision) {
        repository.deleteDraft(releaseId, expectedRevision);
    }

    public AdminWorkspaceResponse saveGraph(
        UUID releaseId,
        long expectedRevision,
        DraftGraphPayload graph
    ) {
        AdminWorkspaceResponse current = repository.workspace(releaseId);
        validator.validateForSave(current.floors(), graph);
        return repository.replaceGraph(
            releaseId,
            expectedRevision,
            graph
        );
    }

    public AdminWorkspaceResponse replaceFloorMap(
        UUID releaseId,
        UUID floorId,
        long expectedRevision,
        MultipartFile file,
        String actor
    ) {
        ValidatedMapImage image = validateMapImage(file);
        return repository.replaceFloorMap(
            releaseId,
            floorId,
            expectedRevision,
            image.mimeType(),
            sha256(image.content()),
            image.width(),
            image.height(),
            image.content(),
            actor(actor)
        );
    }

    @Transactional
    public AdminValidationResponse validate(
        UUID releaseId,
        long expectedRevision,
        String actor
    ) {
        AdminWorkspaceResponse workspace = repository.workspace(releaseId);
        AdminValidationResponse structural = validator.validateForPublish(
            workspace
        );
        RouteRegressionRun regressionRun = regressionService.run(workspace);
        AdminValidationResponse validation = withRouteRegressions(
            structural,
            regressionRun.results()
        );
        repository.recordValidation(
            validation,
            expectedRevision,
            actor(actor),
            regressionRun.configurationRevision()
        );
        return validation;
    }

    private AdminValidationResponse withRouteRegressions(
        AdminValidationResponse structural,
        List<AdminRouteRegressionResult> results
    ) {
        List<AdminValidationResponse.Issue> errors = new ArrayList<>(
            structural.errors()
        );
        List<AdminValidationResponse.Issue> warnings = new ArrayList<>(
            structural.warnings()
        );
        if (results.isEmpty()) {
            warnings.add(
                new AdminValidationResponse.Issue(
                    "NO_ROUTE_REGRESSION_CASE",
                    "release",
                    structural.releaseId(),
                    "当前楼栋尚未配置启用的关键路线回归用例。"
                )
            );
        }
        for (AdminRouteRegressionResult result : results) {
            if (result.passed()) {
                continue;
            }
            AdminValidationResponse.Issue issue =
                new AdminValidationResponse.Issue(
                    "ROUTE_REGRESSION_" + result.resultCode(),
                    "route_regression_case",
                    result.caseId(),
                    result.caseName() + "：" + result.message()
                );
            if (result.critical()) {
                errors.add(issue);
            } else {
                warnings.add(issue);
            }
        }
        return new AdminValidationResponse(
            structural.releaseId(),
            structural.contentRevision(),
            errors.isEmpty(),
            errors,
            warnings,
            results
        );
    }

    public NavigationContextResponse publish(
        UUID releaseId,
        long expectedRevision,
        PublishReleaseRequest request,
        String actor
    ) {
        String reason = request == null ? null : request.reason();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("publish reason is required");
        }
        AdminWorkspaceResponse workspace = repository.workspace(releaseId);
        repository.publish(
            releaseId,
            expectedRevision,
            actor(actor),
            reason
        );
        return contextService.activeContext(workspace.building().id());
    }

    public NavigationContextResponse rollback(
        UUID targetReleaseId,
        PublishReleaseRequest request,
        String actor
    ) {
        String reason = request == null ? null : request.reason();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rollback reason is required");
        }
        AdminWorkspaceResponse workspace = repository.workspace(
            targetReleaseId
        );
        repository.rollback(
            targetReleaseId,
            actor(actor),
            reason
        );
        return contextService.activeContext(workspace.building().id());
    }

    private String actor(String value) {
        if (value == null || value.isBlank()) {
            return "local-admin";
        }
        String actor = value.trim();
        if (actor.length() > 100) {
            throw new IllegalArgumentException(
                "admin user must not exceed 100 characters"
            );
        }
        return actor;
    }

    private ValidatedMapImage validateMapImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("map image is required");
        }
        if (file.getSize() > MAX_MAP_BYTES) {
            throw new IllegalArgumentException(
                "map image must not exceed 15 MB"
            );
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException error) {
            throw new IllegalArgumentException(
                "map image could not be read",
                error
            );
        }

        try (
            ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content)
            )
        ) {
            if (input == null) {
                throw new IllegalArgumentException(
                    "map image must be a PNG or JPEG file"
                );
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                    "map image must be a PNG or JPEG file"
                );
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName()
                    .toLowerCase(Locale.ROOT);
                String mimeType = switch (format) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    default -> throw new IllegalArgumentException(
                        "map image must be a PNG or JPEG file"
                    );
                };
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (
                    width <= 0
                        || height <= 0
                        || (long) width * height > MAX_MAP_PIXELS
                ) {
                    throw new IllegalArgumentException(
                        "map image dimensions are invalid or exceed 50 million pixels"
                    );
                }
                return new ValidatedMapImage(
                    content,
                    mimeType,
                    width,
                    height
                );
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw new IllegalArgumentException(
                "map image could not be decoded",
                error
            );
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private record ValidatedMapImage(
        byte[] content,
        String mimeType,
        int width,
        int height
    ) {
    }
}
