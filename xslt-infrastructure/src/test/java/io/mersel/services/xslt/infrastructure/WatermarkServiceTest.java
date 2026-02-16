package io.mersel.services.xslt.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WatermarkService birim testleri.
 */
@DisplayName("WatermarkService")
class WatermarkServiceTest {

    private WatermarkService watermarkService;

    @BeforeEach
    void setUp() {
        watermarkService = new WatermarkService();
    }

    @Test
    @DisplayName("HTML'e filigran eklenmeli")
    void shouldAddWatermarkToHtml() {
        String html = "<html><head></head><body><p>İçerik</p></body></html>";
        byte[] result = watermarkService.addWatermark(html.getBytes(StandardCharsets.UTF_8), "TASLAK");

        String resultHtml = new String(result, StandardCharsets.UTF_8);
        assertThat(resultHtml).contains("TASLAK");
        assertThat(resultHtml).contains("watermark");
        assertThat(resultHtml).contains("<style>");
    }

    @Test
    @DisplayName("3 adet filigran div eklenmeli")
    void shouldAddThreeWatermarkDivs() {
        String html = "<html><head></head><body><p>İçerik</p></body></html>";
        byte[] result = watermarkService.addWatermark(html.getBytes(StandardCharsets.UTF_8), "TEST");

        String resultHtml = new String(result, StandardCharsets.UTF_8);
        // 3 watermark x 2 divs (top + bottom) = 6 divs
        int count = resultHtml.split("class=\"watermark\"").length - 1;
        assertThat(count).isEqualTo(6);
    }

    @Test
    @DisplayName("Boş içerik için filigran eklenmemeli")
    void shouldReturnSameContentForEmptyInput() {
        byte[] result = watermarkService.addWatermark(null, "TASLAK");
        assertThat(result).isNull();

        result = watermarkService.addWatermark(new byte[0], "TASLAK");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Boş filigran metni için değişiklik yapılmamalı")
    void shouldReturnSameContentForEmptyWatermark() {
        String html = "<html><head></head><body><p>İçerik</p></body></html>";
        byte[] original = html.getBytes(StandardCharsets.UTF_8);
        byte[] result = watermarkService.addWatermark(original, "");

        assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("CSS stili head bölümüne eklenmeli")
    void shouldAddCssStyleToHead() {
        String html = "<html><head><title>Test</title></head><body></body></html>";
        byte[] result = watermarkService.addWatermark(html.getBytes(StandardCharsets.UTF_8), "FİLİGRAN");

        String resultHtml = new String(result, StandardCharsets.UTF_8);
        assertThat(resultHtml).contains("opacity: 0.3");
        assertThat(resultHtml).contains("rotate(-45deg)");
    }
}
