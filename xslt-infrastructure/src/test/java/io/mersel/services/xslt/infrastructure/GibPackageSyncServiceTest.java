package io.mersel.services.xslt.infrastructure;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import io.mersel.services.xslt.infrastructure.config.GibSyncProperties;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * GibPackageSyncService birim testleri.
 */
@DisplayName("GibPackageSyncService")
class GibPackageSyncServiceTest {

    private static final String EFATURA_URL = "https://ebelge.gib.gov.tr/dosyalar/kilavuzlar/e-FaturaPaketi.zip";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tempDir;

    private GibSyncProperties properties;
    private AssetRegistry assetRegistry;
    private XsltMetrics xsltMetrics;

    @BeforeEach
    void setUp() {
        properties = new GibSyncProperties();
        properties.setEnabled(true);
        properties.setTargetPath(tempDir.resolve("target").toString());
        properties.setBaseUrlOverride("http://localhost:" + wireMock.getPort());
        properties.setConnectTimeoutMs(5000);
        properties.setReadTimeoutMs(10000);

        assetRegistry = mock(AssetRegistry.class);
        xsltMetrics = mock(XsltMetrics.class);
    }

    private GibPackageSyncService createService() {
        return new GibPackageSyncService(properties, assetRegistry, xsltMetrics);
    }

    private byte[] createValidZipWithEntries(String... entryNames) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String name : entryNames) {
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write("content".getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("zip_extraction_path_traversal_engeli — ZIP içinde ../../etc/passwd atlanmalı")
    void zip_extraction_path_traversal_engeli() throws Exception {
        byte[] zipBytes = createValidZipWithEntries(
                "../../etc/passwd",
                "safe/schematron/valid.xml"
        );

        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(zipBytes)));

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isTrue();
        assertThat(result.extractedFiles()).doesNotContain("../../etc/passwd");
        assertThat(result.extractedFiles()).anyMatch(f -> f.contains("valid.xml"));

        Path etcPasswd = tempDir.resolve("target").resolve("etc").resolve("passwd");
        assertThat(Files.exists(etcPasswd)).isFalse();
    }

    @Test
    @DisplayName("glob_pattern_eslestirme — **/*.xml dir/file.xml ile eşleşmeli")
    void glob_pattern_eslestirme() throws Exception {
        byte[] zipBytes = createValidZipWithEntries(
                "pack/schematron/invoice.xml",
                "pack/schematron/credit.xml"
        );

        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(zipBytes)));

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isTrue();
        assertThat(result.filesExtracted()).isEqualTo(2);
        assertThat(result.extractedFiles()).anyMatch(f -> f.endsWith("invoice.xml"));
        assertThat(result.extractedFiles()).anyMatch(f -> f.endsWith("credit.xml"));
    }

    @Test
    @DisplayName("bozuk_zip_graceful_error — Geçersiz ZIP açık hata vermeli")
    void bozuk_zip_graceful_error() {
        byte[] invalidZip = "not a zip file at all".getBytes();

        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody(invalidZip)));

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
        assertThat(result.error()).containsIgnoringCase("ZIP");
        assertThat(result.error()).doesNotContain("NullPointerException");
    }

    @Test
    @DisplayName("http_timeout_yonetimi — Timeout açıklayıcı hata vermeli")
    void http_timeout_yonetimi() {
        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(15000)));

        properties.setReadTimeoutMs(100);

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull().isNotEmpty();
        // Error should be descriptive (timeout, interrupted, connection, etc.), not a raw stack trace
        assertThat(result.error().length()).isLessThan(500);
    }

    @Test
    @DisplayName("sync_kapali_false_doner — enabled=false iken sync devre dışı dönmeli")
    void sync_kapali_false_doner() throws Exception {
        properties.setEnabled(false);

        GibPackageSyncService service = createService();

        List<PackageSyncResult> syncAllResults = service.syncAll();
        assertThat(syncAllResults).isEmpty();

        PackageSyncResult syncPackageResult = service.syncPackage("efatura");
        assertThat(syncPackageResult.success()).isFalse();
        assertThat(syncPackageResult.error()).containsIgnoringCase("devre dışı");

        verify(assetRegistry, never()).reload();
    }

    @Test
    @DisplayName("alt_klasor_korunmasi — **/xsd/**/*.xsd alt dizin yapısını korumalı")
    void alt_klasor_korunmasi() throws Exception {
        // e-Defter XSD dosyaları xsd/ altında alt dizinlerle gelir
        byte[] zipBytes = createValidZipWithEntries(
                "edefter/xsd/gl-bus-2006-10-25.xsd",
                "edefter/xsd/subdirectory/gl-cor-content-2006-10-25.xsd",
                "edefter/xsd/another/deep/gl-muc-2006-10-25.xsd",
                "edefter/sch/edefter_kebir.sch"
        );

        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/paketler/e-Defter_Paketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(zipBytes)));

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("edefter");

        assertThat(result.success()).isTrue();

        // XSD dosyaları: alt klasör yapısı korunmalı
        assertThat(result.extractedFiles()).anyMatch(f ->
                f.equals("validator/eledger/schema/gl-bus-2006-10-25.xsd"));
        assertThat(result.extractedFiles()).anyMatch(f ->
                f.equals("validator/eledger/schema/subdirectory/gl-cor-content-2006-10-25.xsd"));
        assertThat(result.extractedFiles()).anyMatch(f ->
                f.equals("validator/eledger/schema/another/deep/gl-muc-2006-10-25.xsd"));

        // SCH dosyaları: düz kalmalı (pattern **/sch/*.sch)
        assertThat(result.extractedFiles()).anyMatch(f ->
                f.equals("validator/eledger/schematron/edefter_kebir.sch"));

        // Disk üzerinde alt dizin yapısı var mı kontrol et
        Path targetBase = tempDir.resolve("target");
        assertThat(Files.exists(targetBase.resolve("validator/eledger/schema/subdirectory/gl-cor-content-2006-10-25.xsd")))
                .isTrue();
        assertThat(Files.exists(targetBase.resolve("validator/eledger/schema/another/deep/gl-muc-2006-10-25.xsd")))
                .isTrue();
    }

    @Test
    @DisplayName("hedef_dizin_olusmasi — Olmayan hedef dizin oluşturulmalı")
    void hedef_dizin_olusmasi() throws Exception {
        Path nonExistentTarget = tempDir.resolve("new-target").resolve("nested");
        properties.setTargetPath(nonExistentTarget.toString());

        byte[] zipBytes = createValidZipWithEntries("schematron/test.xml");

        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(zipBytes)));

        assertThat(Files.exists(nonExistentTarget)).isFalse();

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isTrue();
        assertThat(Files.exists(nonExistentTarget)).isTrue();
        assertThat(Files.isDirectory(nonExistentTarget)).isTrue();
    }
}
