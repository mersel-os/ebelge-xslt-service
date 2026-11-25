package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.CustomDocumentType;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code custom-assets.example/} dizini olduğu gibi asset dizini olarak kullanıldığında
 * içindeki örnek özel belge türünün ({@code SAMPLE_VOUCHER}) doğrudan çalıştığını doğrular.
 */
@DisplayName("custom-assets.example — örnek özel belge türü")
class CustomAssetsExampleTest {

    private static final String EXAMPLE_DIR = "custom-assets.example";
    private static final String SAMPLE = "custom/sample/ornek/Sample-Voucher.xml";
    private static final String SAMPLE_ID = "SAMPLE_VOUCHER";

    @TempDir
    Path assetDir;

    private CustomDocumentTypeRegistry registry;
    private JaxpSchemaValidator validator;
    private String sample;

    @BeforeEach
    void setUp() throws Exception {
        copyTree(findExampleDir(), assetDir);
        sample = Files.readString(assetDir.resolve(SAMPLE));

        AssetManager assetManager = new AssetManager();
        set(AssetManager.class, assetManager, "externalPath", assetDir.toString());
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
    @DisplayName("ornek_tanim_hatasiz_yuklenir_ve_xsdsi_derlenir")
    void ornek_tanim_hatasiz_yuklenir_ve_xsdsi_derlenir() {
        ReloadResult definitions = registry.reload();
        ReloadResult schemas = validator.reload();

        assertThat(definitions.status()).isEqualTo(ReloadResult.Status.OK);
        assertThat(registry.findByRoot("urn:example:sample:voucher-1", "Voucher"))
                .hasValueSatisfying(definition -> {
                    assertThat(definition.id()).isEqualTo(SAMPLE_ID);
                    assertThat(definition.schematronType()).isEqualTo(SchematronValidationType.UBLTR_MAIN);
                });
        for (CustomDocumentType definition : registry.getAll()) {
            assertThat(String.join(" | ", schemas.errors()))
                    .as("örnek tanımların XSD'leri derlenmeli: " + definition.id())
                    .doesNotContain(definition.id());
        }
    }

    @Test
    @DisplayName("ornek_belge_gecerli")
    void ornek_belge_gecerli() {
        reloadAll();

        assertThat(validator.validateCustom(bytes(sample), SAMPLE_ID, List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("zorunlu_eleman_eksikse_satir_bilgili_hata")
    void zorunlu_eleman_eksikse_satir_bilgili_hata() {
        reloadAll();
        String withoutParty = sample.replaceAll("(?s)\\s*<cmn:Party>.*</cmn:Party>", "");
        int lineLine = lineOf(withoutParty, "<v:Line>");

        List<String> errors = validator.validateCustom(bytes(withoutParty), SAMPLE_ID, List.of(), null);

        assertThat(errors).singleElement().asString()
                .startsWith("Satır " + lineLine + ", Sütun ")
                .contains("\"Line\" elementi bu konumda geçersiz")
                .contains("Party");
    }

    @Test
    @DisplayName("goreli_yolla_import_edilen_ortak_tipler_uygulanir")
    void goreli_yolla_import_edilen_ortak_tipler_uygulanir() {
        reloadAll();
        String badAmount = sample.replaceFirst(">100.00</cmn:Amount>", ">abc</cmn:Amount>");
        assertThat(badAmount).isNotEqualTo(sample);

        List<String> errors = validator.validateCustom(bytes(badAmount), SAMPLE_ID, List.of(), null);

        assertThat(errors).isNotEmpty().allSatisfy(error -> assertThat(error)
                .startsWith("Satır " + lineOf(badAmount, "<cmn:Amount") + ", Sütun "));
        assertThat(String.join(" | ", errors)).contains("abc");
    }

    // ── Yardımcılar ─────────────────────────────────────────────────

    private void reloadAll() {
        registry.reload();
        validator.reload();
    }

    /** Test çalışma dizini modül dizinidir; örnek dizin depo kökündedir. */
    private static Path findExampleDir() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(EXAMPLE_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(EXAMPLE_DIR + " dizini bulunamadı");
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path copy = target.resolve(source.relativize(file).toString());
                Files.createDirectories(copy.getParent());
                Files.copy(file, copy);
            }
        }
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
