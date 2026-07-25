package com.medroute.nav.controller;

import com.medroute.nav.dto.NavigationQrCodeRequest;
import com.medroute.nav.navigation.service.NavigationQrCodeService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(allowedHeaders = {"Content-Type", "X-Request-Id"})
@RestController
@RequestMapping("/api/admin/navigation-qr-code")
public class NavigationQrCodeController {
    private final NavigationQrCodeService qrCodeService;

    public NavigationQrCodeController(
        NavigationQrCodeService qrCodeService
    ) {
        this.qrCodeService = qrCodeService;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> generate(
        @RequestBody NavigationQrCodeRequest request
    ) {
        return ResponseEntity
            .ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"navigation-qr-code.png\""
            )
            .body(
                qrCodeService.generatePng(
                    request == null ? null : request.navigationUrl()
                )
            );
    }
}
