package com.medroute.nav.navigation.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Service
public class NavigationQrCodeService {
    private static final int IMAGE_SIZE = 512;
    private static final int MAX_URL_BYTES = 2048;

    public byte[] generatePng(String rawNavigationUrl) {
        String navigationUrl = validate(rawNavigationUrl);
        Map<EncodeHintType, Object> hints = new EnumMap<>(
            EncodeHintType.class
        );
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);

        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                navigationUrl,
                BarcodeFormat.QR_CODE,
                IMAGE_SIZE,
                IMAGE_SIZE,
                hints
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException error) {
            throw new IllegalStateException("无法生成导航二维码。", error);
        }
    }

    private String validate(String rawNavigationUrl) {
        if (rawNavigationUrl == null || rawNavigationUrl.isBlank()) {
            throw new IllegalArgumentException("导航地址不能为空。");
        }
        String navigationUrl = rawNavigationUrl.trim();
        if (
            navigationUrl.getBytes(StandardCharsets.UTF_8).length
                > MAX_URL_BYTES
        ) {
            throw new IllegalArgumentException(
                "导航地址的 UTF-8 长度不能超过 2048 字节。"
            );
        }

        URI uri;
        try {
            uri = URI.create(navigationUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("导航地址格式无效。", error);
        }
        String scheme = uri.getScheme() == null
            ? ""
            : uri.getScheme().toLowerCase(Locale.ROOT);
        if (
            (!scheme.equals("http") && !scheme.equals("https"))
                || uri.getHost() == null
                || uri.getUserInfo() != null
        ) {
            throw new IllegalArgumentException(
                "导航地址必须是有效的 HTTP 或 HTTPS 地址。"
            );
        }
        return navigationUrl;
    }
}
