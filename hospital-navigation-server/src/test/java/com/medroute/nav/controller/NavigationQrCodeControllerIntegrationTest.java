package com.medroute.nav.controller;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.medroute.nav.navigation.service.NavigationQrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NavigationQrCodeControllerIntegrationTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new NavigationQrCodeController(new NavigationQrCodeService())
            )
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
    }

    @Test
    void generatesADecodableNavigationQrCode() throws Exception {
        String navigationUrl =
            "https://nav.example.test/hospital-map-demo/multifloor.html"
                + "?building=building-1&startPoi=P-ENTRANCE";

        MvcResult result = mockMvc.perform(
                post("/api/admin/navigation-qr-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"navigationUrl":"%s"}
                        """.formatted(navigationUrl)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();

        BufferedImage image = ImageIO.read(
            new ByteArrayInputStream(
                result.getResponse().getContentAsByteArray()
            )
        );
        assertThat(image.getWidth()).isEqualTo(512);
        assertThat(image.getHeight()).isEqualTo(512);

        BinaryBitmap bitmap = new BinaryBitmap(
            new HybridBinarizer(new BufferedImageLuminanceSource(image))
        );
        Result decoded = new MultiFormatReader().decode(bitmap);
        assertThat(decoded.getText()).isEqualTo(navigationUrl);
    }

    @Test
    void rejectsNonHttpNavigationUrls() throws Exception {
        mockMvc.perform(
                post("/api/admin/navigation-qr-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"navigationUrl":"javascript:alert(1)"}
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void rejectsANullRequestBody() throws Exception {
        mockMvc.perform(
                post("/api/admin/navigation-qr-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("null")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    }
}
