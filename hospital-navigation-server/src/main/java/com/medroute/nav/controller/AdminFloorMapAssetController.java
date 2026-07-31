package com.medroute.nav.controller;

import com.medroute.nav.navigation.repository.JdbcFloorMapAssetRepository;
import com.medroute.nav.navigation.repository.JdbcFloorMapAssetRepository.FloorMapAsset;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@CrossOrigin(
    allowedHeaders = {"X-Admin-User", "X-Request-Id"},
    exposedHeaders = {"ETag", "X-Request-Id"}
)
@RestController
@RequestMapping("/api/admin/map-images")
public class AdminFloorMapAssetController {
    private final JdbcFloorMapAssetRepository repository;

    public AdminFloorMapAssetController(
        JdbcFloorMapAssetRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping("/{revisionId}")
    public ResponseEntity<byte[]> image(@PathVariable UUID revisionId) {
        FloorMapAsset asset = repository.findForAdmin(revisionId);
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(asset.mimeType()))
            .cacheControl(
                CacheControl.maxAge(Duration.ofDays(365))
                    .cachePrivate()
                    .immutable()
            )
            .eTag(asset.sha256())
            .body(asset.content());
    }
}
