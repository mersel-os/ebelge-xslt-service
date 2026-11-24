package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.XsdOverride;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Asset dizininde tanımlanan özel belge türlerinin XSD doğrulaması — gerçek dosyalarla.
 * Örnek şema seti ({@code fixtures/custom-document-types}) ortak şemayı göreceli yolla içe aktarır.
 */
@DisplayName("JaxpSchemaValidator — özel belge türleri")
class JaxpSchemaValidatorCustomTypeTest {

    private static final String TYPE_ID = "SAMPLE_VOUCHER";
    private static final String MAIN_XSD = "custom/sample/maindoc/Sample-Voucher-1.0.xsd";
    private static final String DEFINITION = """
            document-types:
              - id: SAMPLE_VOUCHER
                root-namespace: "urn:example:sample:voucher-1"
                root-element: Voucher
                xsd: custom/sample/maindoc/Sample-Voucher-1.0.xsd
            """;

    @TempDir
    Path assetDir;

    private CustomDocumentTypeRegistry registry;
    private JaxpSchemaValidator validator;
    private String validXml;

    @BeforeEach
    void setUp() throws Exception {
        Path fixtures = Path.of(getClass().getResource("/fixtures/custom-document-types").toURI());
        try (Stream<Path> files = Files.walk(fixtures.resolve("custom"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = assetDir.resolve(fixtures.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
        validXml = Files.readString(fixtures.resolve("Sample-Voucher.xml"));
        Files.writeString(assetDir.resolve(CustomDocumentTypeRegistry.ASSET_PATH), DEFINITION);

        AssetManager assetManager = new AssetManager();
        Field field = AssetManager.class.getDeclaredField("externalPath");
        field.setAccessible(true);
        field.set(assetManager, assetDir.toString());
        assetManager.init();

        registry = new CustomDocumentTypeRegistry(assetManager);
        validator = new JaxpSchemaValidator(assetManager, mock(XsltMetrics.class), registry);
        Field maxSize = JaxpSchemaValidator.class.getDeclaredField("xsdOverrideCacheMaxSize");
        maxSize.setAccessible(true);
        maxSize.setInt(validator, 50);
        Field ttl = JaxpSchemaValidator.class.getDeclaredField("xsdOverrideCacheTtlHours");
        ttl.setAccessible(true);
        ttl.setInt(validator, 1);
        validator.init();
    }

    @Test
    @DisplayName("tanimli_tur_derlenir_ve_gecerli_belge_gecer")
    void tanimli_tur_derlenir_ve_gecerli_belge_gecer() {
        ReloadResult result = reloadAll();

        assertThat(String.join(" | ", result.errors()))
                .as("GİB paketleri yokken yerleşik tipler hata verir, özel tür vermez")
                .contains("INVOICE")
                .doesNotContain(TYPE_ID);
        assertThat(validator.validateCustom(bytes(validXml), TYPE_ID, List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("zorunlu_eleman_eksikse_satir_bilgili_hata")
    void zorunlu_eleman_eksikse_satir_bilgili_hata() {
        reloadAll();
        String withoutParty = validXml.replaceAll("(?s)\\s*<cmn:Party>.*</cmn:Party>", "");
        int lineOfLine = lineOf(withoutParty, "<v:Line>");

        List<String> errors = validator.validateCustom(bytes(withoutParty), TYPE_ID, List.of(), null);

        assertThat(errors).singleElement().asString()
                .startsWith("Satır " + lineOfLine + ", Sütun ")
                .contains("\"Line\" elementi bu konumda geçersiz")
                .contains("Party");
    }

    @Test
    @DisplayName("ice_aktarilan_ortak_semadaki_kural_uygulanir")
    void ice_aktarilan_ortak_semadaki_kural_uygulanir() {
        reloadAll();
        String withoutCurrency = validXml.replace(" currencyID=\"TRY\"", "");

        List<String> errors = validator.validateCustom(bytes(withoutCurrency), TYPE_ID, List.of(), null);

        assertThat(errors).singleElement().asString().startsWith("Satır ").contains("currencyID");
    }

    @Test
    @DisplayName("xsd_dosyasi_yoksa_reload_ve_dogrulama_hata_verir")
    void xsd_dosyasi_yoksa_reload_ve_dogrulama_hata_verir() throws Exception {
        Files.delete(assetDir.resolve(MAIN_XSD));

        ReloadResult result = reloadAll();

        assertThat(String.join(" | ", result.errors()))
                .contains(TYPE_ID + " özel belge türü XSD şeması yüklenemedi (" + MAIN_XSD + ")");
        assertThat(validator.validateCustom(bytes(validXml), TYPE_ID, List.of(), null))
                .singleElement().asString()
                .contains(TYPE_ID + " özel belge türü için XSD şeması yüklenemedi")
                .contains("custom-document-types.yml");
    }

    @Test
    @DisplayName("tanim_kaldirilinca_tur_taninmaz")
    void tanim_kaldirilinca_tur_taninmaz() throws Exception {
        reloadAll();
        Files.delete(assetDir.resolve(CustomDocumentTypeRegistry.ASSET_PATH));

        reloadAll();

        assertThat(registry.findByRoot("urn:example:sample:voucher-1", "Voucher")).isEmpty();
        assertThat(validator.validateCustom(bytes(validXml), TYPE_ID, List.of(), null))
                .singleElement().asString().contains("tanımlı değil");
    }

    @Test
    @DisplayName("profil_xsd_override_ozel_turde_calisir")
    void profil_xsd_override_ozel_turde_calisir() {
        reloadAll();
        String withoutParty = validXml.replaceAll("(?s)\\s*<cmn:Party>.*</cmn:Party>", "");
        var overrides = List.of(new XsdOverride("cmn:Party", "0", null));

        assertThat(validator.validateCustom(bytes(withoutParty), TYPE_ID, overrides, "ozel-profil")).isEmpty();
        assertThat(validator.validateCustom(bytes(withoutParty), TYPE_ID, List.of(), null)).hasSize(1);
        assertThat(assetDir.resolve("auto-generated/schema-overrides/" + TYPE_ID + "_ozel-profil_override.xsd"))
                .exists();
    }

    /** AssetRegistry sırası: özel tanımlar XSD derlemesinden önce yüklenir. */
    private ReloadResult reloadAll() {
        registry.reload();
        return validator.reload();
    }

    private static int lineOf(String xml, String marker) {
        int index = xml.indexOf(marker);
        assertThat(index).isNotNegative();
        return (int) xml.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
