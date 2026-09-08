package io.mersel.services.xslt.infrastructure;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.mersel.services.xslt.application.models.GibPackageDefinition;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import io.mersel.services.xslt.infrastructure.config.GibSyncProperties;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
    private static final String EDOVIZ_PATH =
            "/dosyalar/e-doviz_ve_kiymetlimaden_alim-satim_paketi_v1.2.rar";
    private static final String EDEKONT_PATH = "/dosyalar/eDekont_Paketi.rar";
    private static final byte[] VALID_RAR = Base64.getDecoder().decode(
            "UmFyIRoHAM+QcwAADQAAAAAAAAB8zXQgkC0ADQAAAAQAAAAD4Tl7zCeTJEEdMwsAtIEAAGZvb1xiYXIudHh0AMAACL8IrvLDGH6f/ZLdiiN04IAjAAAAAAAAAAAAAwAAAAAnkyRBFDADAP1BAABmb2/EPXsAQAcA");

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
        return new GibPackageSyncService(properties, assetRegistry, xsltMetrics,
                new EmbeddedXsltExtractor());
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

    private byte[] createValidZipWithContents(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var entryContent : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entryContent.getKey()));
                zos.write(entryContent.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private byte[] createUblWithEmbeddedXslt(String embeddedContent) {
        String base64 = Base64.getEncoder().encodeToString(embeddedContent.getBytes());
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="application.xslt"
                                encodingCode="Base64">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </CreditNote>""".formatted(base64);
        return xml.getBytes();
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
    @DisplayName("rar_magic_bytes_ile_taninir — zorunlu pattern yoksa sync başarısız olur")
    void rar_magic_bytes_ile_taninir_ama_zorunlu_pattern_yoksa_hata_verir() {
        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.rar")
                        .withBody(VALID_RAR)));

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isFalse();
        assertThat(result.filesExtracted()).isZero();
        assertThat(result.error()).contains("zorunlu pattern");
        verify(assetRegistry, never()).reload();
    }

    @Test
    @DisplayName("eslesme_yoksa_mevcut_asset_korunur — hedef dizin temizlenmemeli")
    void eslesme_yoksa_mevcut_asset_korunur() throws Exception {
        byte[] archiveBytes = createValidZipWithEntries("unrelated/file.txt");
        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(archiveBytes)));

        Path existingAsset = tempDir.resolve(
                "target/validator/ubl-tr-package/schematron/existing.xml");
        Files.createDirectories(existingAsset.getParent());
        Files.writeString(existingAsset, "existing");

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("zorunlu pattern");
        assertThat(existingAsset).exists().hasContent("existing");
        verify(assetRegistry, never()).reload();
    }

    @Test
    @DisplayName("sonraki_kural_eslesmezse_onceki_hedef_korunur — tüm kurallar önceden doğrulanır")
    void sonraki_kural_eslesmezse_onceki_hedef_korunur() throws Exception {
        byte[] archiveBytes = createValidZipWithEntries("alim.xslt");
        wireMock.stubFor(get(urlPathEqualTo(EDOVIZ_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(archiveBytes)));

        Path existingAsset = tempDir.resolve("target/default_transformers/eDovizAlim_Base.xslt");
        Files.createDirectories(existingAsset.getParent());
        Files.writeString(existingAsset, "existing");

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("edoviz");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("satim.xslt");
        assertThat(existingAsset).exists().hasContent("existing");
        verify(assetRegistry, never()).reload();
    }

    @Test
    @DisplayName("arsiv_entry_limiti — aşırı sayıda girdi reddedilir")
    void arsiv_entry_limiti() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i <= 10_000; i++) {
                zos.putNextEntry(new ZipEntry("entries/entry-" + i + ".txt"));
                zos.closeEntry();
            }
        }
        wireMock.stubFor(get(urlPathEqualTo("/dosyalar/kilavuzlar/e-FaturaPaketi.zip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(baos.toByteArray())));

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("efatura");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("entry sayısı sınırı");
        verify(assetRegistry, never()).reload();
    }

    @Test
    @DisplayName("goruntuleme_paketleri_listelenir — Döviz, dekont ve gider pusulası paketleri mevcut")
    void goruntuleme_paketleri_listelenir() {
        GibPackageSyncService service = createService();

        assertThat(service.getAvailablePackages())
                .extracting(GibPackageDefinition::id)
                .contains("edoviz", "edekont", "egider-pusulasi");

        GibPackageDefinition edoviz = service.getAvailablePackages().stream()
                .filter(pkg -> pkg.id().equals("edoviz"))
                .findFirst()
                .orElseThrow();
        assertThat(edoviz.fileMapping())
                .anySatisfy(mapping -> {
                    assertThat(mapping.zipPathPattern()).isEqualTo("alim.xslt");
                    assertThat(mapping.targetFileName()).isEqualTo("eDovizAlim_Base.xslt");
                });

        // Her ürün paketi kendi eArsivRaporu şemasını da getirir; hedef dizinler
        // ayrıdır çünkü dosya adı hepsinde eArsiv.xsd.
        assertThat(service.getAvailablePackages())
                .filteredOn(pkg -> List.of("edoviz", "edekont", "egider-pusulasi").contains(pkg.id()))
                .allSatisfy(pkg -> assertThat(pkg.fileMapping())
                        .anySatisfy(mapping -> {
                            assertThat(mapping.zipPathPattern()).endsWith("eArsiv.xsd");
                            assertThat(mapping.targetDir()).startsWith("validator/earchive-");
                        }));
    }

    @Test
    @DisplayName("sabit_hedef_adi_paylasilan_dizini_korur — GİB adları servis standardına çevrilir")
    void sabit_hedef_adi_paylasilan_dizini_korur() throws Exception {
        byte[] archiveBytes = createValidZipWithEntries(
                "alim.xslt",
                "satim.xslt",
                "eArsiv.xsd",
                "earsiv_schematron.xsl"
        );
        wireMock.stubFor(get(urlPathEqualTo(EDOVIZ_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(archiveBytes)));

        Path existingTemplate = tempDir.resolve("target/default_transformers/eInvoice_Base.xslt");
        Files.createDirectories(existingTemplate.getParent());
        Files.writeString(existingTemplate, "existing");

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("edoviz");

        assertThat(result.success()).isTrue();
        assertThat(result.filesExtracted()).isEqualTo(4);
        assertThat(Files.exists(existingTemplate)).isTrue();
        assertThat(Files.exists(tempDir.resolve("target/default_transformers/eDovizAlim_Base.xslt"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("target/default_transformers/eDovizSatim_Base.xslt"))).isTrue();
        // Rapor şeması kendi dizinine iner — dosya adı her pakette eArsiv.xsd olduğu için
        // genel e-Arşiv şemasıyla aynı dizini paylaşamaz.
        assertThat(Files.exists(tempDir.resolve("target/validator/earchive-edoviz/schema/eArsiv.xsd"))).isTrue();
        assertThat(Files.exists(
                tempDir.resolve("target/validator/earchive-edoviz/schematron/earsiv_schematron.xsl"))).isTrue();
    }

    @Test
    @DisplayName("edekont_gomulu_xslt_cikarilir — Placeholder atlanıp geçerli şablon hedefe yazılmalı")
    void edekont_gomulu_xslt_cikarilir() throws Exception {
        String validXslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE xsl:stylesheet [<!ENTITY nbsp "&#160;">]>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/"><html><body>Dekont&nbsp;</body></html></xsl:template>
                </xsl:stylesheet>""";
        byte[] archiveBytes = createValidZipWithContents(Map.of(
                "e-Dekont Paketi/dekont.xml", createUblWithEmbeddedXslt("BASE64"),
                "e-Dekont Paketi/ParaAlma.xml", createUblWithEmbeddedXslt(validXslt),
                "e-Dekont Paketi/eArsiv.xsd", "<xs:schema/>".getBytes(StandardCharsets.UTF_8)
        ));
        wireMock.stubFor(get(urlPathEqualTo(EDEKONT_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(archiveBytes)));

        GibPackageSyncService service = createService();
        PackageSyncResult result = service.syncPackage("edekont");

        Path targetXslt = tempDir.resolve("target/default_transformers/eDekont_Base.xslt");
        assertThat(result.success()).isTrue();
        assertThat(result.filesExtracted()).isEqualTo(2);
        assertThat(result.extractedFiles()).contains(
                "default_transformers/eDekont_Base.xslt",
                "validator/earchive-edekont/schema/eArsiv.xsd");
        assertThat(targetXslt).content()
                .contains("<!DOCTYPE xsl:stylesheet")
                .contains("xsl:stylesheet")
                .contains("Dekont&nbsp;");
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

        byte[] zipBytes = createValidZipWithEntries("pack/schematron/test.xml");

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
