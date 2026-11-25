package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.ReloadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WatermarkService birim testleri.
 */
@DisplayName("WatermarkService")
class WatermarkServiceTest {

    private static final String LAYER_OPEN = "<div id=\"watermark\">";

    @TempDir
    Path assetDir;

    private WatermarkService watermarkService;

    @BeforeEach
    void setUp() {
        var assetManager = new AssetManager();
        ReflectionTestUtils.setField(assetManager, "externalPath", assetDir.toString());
        assetManager.init();
        watermarkService = new WatermarkService(assetManager);
        watermarkService.reload();
    }

    private void writeCustomStyle(byte[] content) throws IOException {
        Files.write(assetDir.resolve(WatermarkService.CUSTOM_STYLE_ASSET), content);
    }

    private void writeCustomStyle(String content) throws IOException {
        writeCustomStyle(content.getBytes(StandardCharsets.UTF_8));
    }

    private String apply(String html, String watermarkText) {
        byte[] result = watermarkService.addWatermark(html.getBytes(StandardCharsets.UTF_8), watermarkText);
        return new String(result, StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    @Test
    @DisplayName("HTML'e filigran eklenmeli")
    void shouldAddWatermarkToHtml() {
        String resultHtml = apply("<html><head></head><body><p>İçerik</p></body></html>", "TASLAK");

        assertThat(resultHtml).contains("TASLAK");
        assertThat(resultHtml).contains(LAYER_OPEN);
        assertThat(resultHtml).contains("<style type=\"text/css\">");
    }

    @Test
    @DisplayName("Filigran belge boyunca döşenmeli: 200 satır x 3 metin, tek katman")
    void shouldTileWatermarkRowsAcrossDocument() {
        String resultHtml = apply("<html><head></head><body><p>İçerik</p></body></html>", "TEST");

        assertThat(occurrences(resultHtml, LAYER_OPEN)).isEqualTo(1);
        assertThat(occurrences(resultHtml, "<div>TEST</div>")).isEqualTo(WatermarkService.DEFAULT_ROW_COUNT * 3);
        assertThat(occurrences(resultHtml, "<div><div>TEST</div><div>TEST</div><div>TEST</div></div>"))
                .isEqualTo(WatermarkService.DEFAULT_ROW_COUNT);
        assertThat(resultHtml).contains("height: 300px !important");
    }

    @Test
    @DisplayName("Katman html'e göre absolute olmalı ki belgeyle birlikte kaysın ve her sayfaya düşsün")
    void shouldAnchorLayerToWholeDocument() {
        String resultHtml = apply("<html><head></head><body></body></html>", "TASLAK");

        assertThat(resultHtml).contains("html { position: relative !important; }");
        assertThat(resultHtml).contains("position: absolute !important; top: 0 !important; right: 0 !important; bottom: 0 !important; left: 0 !important;");
        assertThat(resultHtml).contains("overflow: hidden !important");
    }

    @Test
    @DisplayName("Baskıda katman fixed olmalı ki kısa son sayfa dahil her sayfaya basılsın")
    void shouldRepeatLayerOnEveryPrintedPage() {
        String resultHtml = apply("<html><head></head><body></body></html>", "TASLAK");

        String printRule = "@media print {\n    #watermark { position: fixed !important; }\n}";
        assertThat(resultHtml).contains(printRule);
        // Baskı kuralı ekran kuralından sonra gelmeli ki eşit özgüllükte onu ezsin
        assertThat(resultHtml.indexOf(printRule))
                .isGreaterThan(resultHtml.indexOf("#watermark {\n    position: absolute !important;"));
        assertThat(occurrences(resultHtml, "position: fixed")).isEqualTo(1);
    }

    @Test
    @DisplayName("Stiller id seçici ve !important ile tasarım stillerinden korunmalı")
    void shouldProtectStylesWithIdSelectorAndImportant() {
        String resultHtml = apply("<html><head></head><body></body></html>", "TASLAK");

        assertThat(resultHtml).contains("#watermark > div > div {");
        assertThat(resultHtml).contains("-webkit-transform: rotate(-45deg) !important; transform: rotate(-45deg) !important;");
        assertThat(resultHtml).contains("color: rgba(255, 0, 0, 0.3) !important");
        assertThat(resultHtml).contains("opacity: 1 !important");
        assertThat(resultHtml).contains("#watermark > div > div:nth-child(1) { left: 10% !important; }");
        assertThat(resultHtml).contains("#watermark > div > div:nth-child(2) { left: 39% !important; }");
        assertThat(resultHtml).contains("#watermark > div > div:nth-child(3) { left: 68% !important; }");
        assertThat(resultHtml).doesNotContain("nth-child(4)");
    }

    @Test
    @DisplayName("xslt.watermark.count satır başına metin sayısını belirlemeli")
    void shouldHonourConfiguredCountPerRow() {
        ReflectionTestUtils.setField(watermarkService, "numberOfWatermarks", 2);

        String resultHtml = apply("<html><head></head><body></body></html>", "TEST");

        assertThat(occurrences(resultHtml, "<div>TEST</div>")).isEqualTo(WatermarkService.DEFAULT_ROW_COUNT * 2);
        assertThat(resultHtml).contains("nth-child(1) { left: 10% !important; }");
        assertThat(resultHtml).contains("nth-child(2) { left: 56% !important; }");
        assertThat(resultHtml).doesNotContain("nth-child(3)");
    }

    @Test
    @DisplayName("CSS stili head bölümüne, katman body'nin son çocuğu olarak eklenmeli")
    void shouldPlaceStyleInHeadAndLayerAtEndOfBody() {
        String html = "<html><head><title>Test</title></head><body><table><tr><td>100,00</td></tr></table></body></html>";
        String resultHtml = apply(html, "FİLİGRAN");

        int headOpen = resultHtml.indexOf("<head>");
        int style = resultHtml.indexOf("<style");
        int headClose = resultHtml.indexOf("</head>");
        int table = resultHtml.indexOf("</table>");
        int layer = resultHtml.indexOf(LAYER_OPEN);

        assertThat(style).isGreaterThan(headOpen).isLessThan(headClose);
        assertThat(resultHtml).startsWith("<html><head><style");
        assertThat(resultHtml).contains("</style><title>Test</title></head>");
        assertThat(layer).isGreaterThan(table);
        assertThat(resultHtml).endsWith("</div></div></body></html>");
    }

    @Test
    @DisplayName("Filigran metni HTML escape edilmeli (XSS)")
    void shouldEscapeWatermarkText() {
        String resultHtml = apply("<html><head></head><body></body></html>", "<script>alert(\"x\")</script> & <b>");

        assertThat(resultHtml).doesNotContain("<script>");
        assertThat(resultHtml).doesNotContain("<b>");
        assertThat(resultHtml).contains("<div>&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &lt;b&gt;</div>");
    }

    @Test
    @DisplayName("Türkçe filigran metni UTF-8 olarak korunmalı")
    void shouldKeepUtf8Text() {
        String resultHtml = apply("<html><head></head><body>Gönderim Özeti</body></html>", "GEÇERSİZDİR");

        assertThat(resultHtml).contains("Gönderim Özeti" + LAYER_OPEN);
        assertThat(resultHtml).contains("<div>GEÇERSİZDİR</div>");
    }

    @Test
    @DisplayName("head yoksa stil katmanla birlikte body sonuna eklenmeli")
    void shouldPlaceStyleBeforeLayerWhenHeadMissing() {
        String resultHtml = apply("<html><body><header>Başlık</header><p>İçerik</p></body></html>", "TASLAK");

        // <header> etiketi <head> sanılmamalı
        assertThat(resultHtml).startsWith("<html><body><header>Başlık</header><p>İçerik</p><style");
        assertThat(resultHtml.indexOf("</style>" + LAYER_OPEN)).isPositive();
        assertThat(resultHtml).endsWith("</div></div></body></html>");
    }

    @Test
    @DisplayName("</body> yoksa filigran eklenmemeli, içerik aynen dönmeli")
    void shouldReturnContentUnchangedWhenBodyMissing() {
        byte[] original = "<html><head></head><table><tr><td>1</td></tr></table></html>".getBytes(StandardCharsets.UTF_8);

        byte[] result = watermarkService.addWatermark(original, "TASLAK");

        assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("Büyük harfli etiketlerde de filigran eklenmeli")
    void shouldHandleUpperCaseTags() {
        String resultHtml = apply("<HTML><HEAD></HEAD><BODY><P>X</P></BODY></HTML>", "TASLAK");

        assertThat(resultHtml).startsWith("<HTML><HEAD><style");
        assertThat(resultHtml).contains("<P>X</P>" + LAYER_OPEN);
        assertThat(resultHtml).endsWith("</div></div></BODY></HTML>");
    }

    @Test
    @DisplayName("Katman son </body>'den önce eklenmeli")
    void shouldInsertBeforeLastClosingBody() {
        String html = "<html><head></head><body><script>var s = '</body>';</script><p>Son</p></body></html>";
        String resultHtml = apply(html, "TASLAK");

        assertThat(resultHtml).contains("<script>var s = '</body>';</script><p>Son</p>" + LAYER_OPEN);
    }

    @Test
    @DisplayName("head niteliklerindeki $ ve \\ karakterleri hata vermemeli")
    void shouldTolerateRegexSpecialCharsInHead() {
        String resultHtml = apply("<html><head data-x=\"$1 \\d\"></head><body></body></html>", "TASLAK");

        assertThat(resultHtml).startsWith("<html><head data-x=\"$1 \\d\"><style");
        assertThat(resultHtml).contains(LAYER_OPEN);
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

        assertThat(watermarkService.addWatermark(original, "")).isEqualTo(original);
        assertThat(watermarkService.addWatermark(original, "   ")).isEqualTo(original);
        assertThat(watermarkService.addWatermark(original, null)).isEqualTo(original);
    }

    // ── Özelleştirme: xslt.watermark.rows ────────────────────────────

    @Test
    @DisplayName("xslt.watermark.rows satır sayısını belirlemeli, 1–1000 aralığına sıkıştırılmalı")
    void shouldHonourConfiguredRowsWithinBounds() {
        String html = "<html><head></head><body></body></html>";

        ReflectionTestUtils.setField(watermarkService, "numberOfRows", 5);
        assertThat(occurrences(apply(html, "T"), "<div>T</div>")).isEqualTo(5 * 3);

        ReflectionTestUtils.setField(watermarkService, "numberOfRows", 0);
        assertThat(occurrences(apply(html, "T"), "<div>T</div>")).isEqualTo(3);

        ReflectionTestUtils.setField(watermarkService, "numberOfRows", 50_000);
        assertThat(occurrences(apply(html, "T"), "<div>T</div>")).isEqualTo(WatermarkService.MAX_ROW_COUNT * 3);
    }

    // ── Özelleştirme: watermark.css ──────────────────────────────────

    @Test
    @DisplayName("watermark.css yoksa yalnız dahili stil kullanılmalı")
    void shouldUseBuiltInStyleWhenNoCustomStyle() {
        ReloadResult result = watermarkService.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.OK);
        assertThat(result.loadedCount()).isZero();
        assertThat(apply("<html><head></head><body></body></html>", "TASLAK"))
                .doesNotContain("/* " + WatermarkService.CUSTOM_STYLE_ASSET + " */");
    }

    @Test
    @DisplayName("watermark.css dahili stilden sonra, </style>'dan önce eklenmeli")
    void shouldAppendCustomStyleAfterBuiltInStyle() throws IOException {
        String custom = "#watermark > div > div { color: rgba(0, 0, 255, 0.2) !important; font-size: 48px !important; }";
        writeCustomStyle(custom);

        ReloadResult result = watermarkService.reload();
        String resultHtml = apply("<html><head></head><body></body></html>", "TASLAK");

        assertThat(result.status()).isEqualTo(ReloadResult.Status.OK);
        assertThat(result.loadedCount()).isEqualTo(1);
        int builtIn = resultHtml.indexOf("color: rgba(255, 0, 0, 0.3) !important");
        int lastBuiltInRule = resultHtml.indexOf("nth-child(3) { left: 68% !important; }");
        int customIndex = resultHtml.indexOf(custom);
        int styleClose = resultHtml.indexOf("</style>");
        assertThat(customIndex).isGreaterThan(builtIn).isGreaterThan(lastBuiltInRule).isLessThan(styleClose);
    }

    @Test
    @DisplayName("Sonradan eklenen, değişen ve silinen watermark.css reload ile uygulanmalı")
    void shouldPickUpCustomStyleChangesOnReload() throws IOException {
        String html = "<html><head></head><body></body></html>";
        assertThat(apply(html, "TASLAK")).doesNotContain("font-size: 48px");

        writeCustomStyle("#watermark > div > div { font-size: 48px !important; }");
        watermarkService.reload();
        assertThat(apply(html, "TASLAK")).contains("font-size: 48px !important;");

        writeCustomStyle("#watermark > div > div { font-size: 20px !important; }");
        watermarkService.reload();
        assertThat(apply(html, "TASLAK")).contains("font-size: 20px !important;").doesNotContain("font-size: 48px");

        Files.delete(assetDir.resolve(WatermarkService.CUSTOM_STYLE_ASSET));
        watermarkService.reload();
        assertThat(apply(html, "TASLAK")).doesNotContain("font-size: 20px");
    }

    @Test
    @DisplayName("</style> içeren watermark.css reddedilmeli (HTML enjeksiyonu)")
    void shouldRejectCustomStyleThatClosesStyleElement() throws IOException {
        writeCustomStyle("#watermark { color: red; }</STYLE ><script>alert(1)</script><style>");

        ReloadResult result = watermarkService.reload();
        String resultHtml = apply("<html><head></head><body></body></html>", "TASLAK");

        assertThat(result.status()).isEqualTo(ReloadResult.Status.FAILED);
        assertThat(result.errors()).singleElement().asString().contains("</style");
        assertThat(resultHtml).doesNotContain("<script>").doesNotContain("color: red;");
        assertThat(resultHtml).contains(LAYER_OPEN);
    }

    @Test
    @DisplayName("Geçersiz watermark.css önceki özel stili de kaldırmalı")
    void shouldDropPreviousCustomStyleWhenNewOneIsInvalid() throws IOException {
        writeCustomStyle("#watermark > div > div { font-size: 48px !important; }");
        watermarkService.reload();

        writeCustomStyle("</style>");
        ReloadResult result = watermarkService.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.FAILED);
        assertThat(apply("<html><head></head><body></body></html>", "TASLAK")).doesNotContain("font-size: 48px");
    }

    @Test
    @DisplayName("64 KB'tan büyük watermark.css reddedilmeli")
    void shouldRejectOversizedCustomStyle() throws IOException {
        writeCustomStyle("/*" + "x".repeat(WatermarkService.MAX_CUSTOM_STYLE_BYTES) + "*/");

        ReloadResult result = watermarkService.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.FAILED);
        assertThat(apply("<html><head></head><body></body></html>", "TASLAK")).doesNotContain("xxxx");
    }

    @Test
    @DisplayName("UTF-8 olmayan watermark.css reddedilmeli")
    void shouldRejectNonUtf8CustomStyle() throws IOException {
        writeCustomStyle(new byte[]{'a', (byte) 0xC3, (byte) 0x28, 'b'});

        assertThat(watermarkService.reload().status()).isEqualTo(ReloadResult.Status.FAILED);
    }

    @Test
    @DisplayName("watermark.css başındaki BOM atılmalı, Türkçe karakterler korunmalı")
    void shouldStripBomFromCustomStyle() throws IOException {
        writeCustomStyle("﻿#watermark > div > div::after { content: \" — ÖRNEK\" !important; }");

        assertThat(watermarkService.reload().status()).isEqualTo(ReloadResult.Status.OK);
        String resultHtml = apply("<html><head></head><body></body></html>", "TASLAK");
        assertThat(resultHtml).contains("*/\n#watermark > div > div::after { content: \" — ÖRNEK\" !important; }\n</style>");
        assertThat(resultHtml).doesNotContain("﻿");
    }

    @Test
    @DisplayName("Asset dizini yapılandırılmamışsa reload başarılı olmalı, dahili stil kullanılmalı")
    void shouldWorkWithoutAssetDirectory() {
        var service = new WatermarkService(new AssetManager());

        ReloadResult result = service.reload();
        byte[] out = service.addWatermark("<html><head></head><body></body></html>".getBytes(StandardCharsets.UTF_8), "TASLAK");

        assertThat(result.status()).isEqualTo(ReloadResult.Status.OK);
        assertThat(new String(out, StandardCharsets.UTF_8)).contains(LAYER_OPEN);
    }
}
