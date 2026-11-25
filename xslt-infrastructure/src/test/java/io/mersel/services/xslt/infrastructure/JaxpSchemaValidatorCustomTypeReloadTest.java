package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.ReloadResult;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Özel belge türü tanımları ile XSD derlemesi arasındaki reload penceresi — gerçek dosyalarla, mock'suz.
 * <p>
 * {@link AssetRegistry} bileşenleri sırayla yükler: tanımlar yayımlandıktan sonra XSD bileşeni
 * bitene kadar geçen sürede gelen istekler ya eski şemayla ya da "şema yüklenemedi" hatasıyla
 * karşılanmamalı. Testler bu pencereyi yalnız {@link CustomDocumentTypeRegistry#reload()} çağırarak
 * kalıcı hale getirir.
 */
@DisplayName("JaxpSchemaValidator — özel tür reload penceresi")
class JaxpSchemaValidatorCustomTypeReloadTest {

    private static final String NS_A = "urn:example:reload:a";
    private static final String NS_B = "urn:example:reload:b";

    @TempDir
    Path assetDir;

    private CustomDocumentTypeRegistry registry;
    private JaxpSchemaValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        writeAsset("custom/a/A-first.xsd", schema(NS_A, "Doc", "First"));
        writeAsset("custom/a/A-second.xsd", schema(NS_A, "Doc", "Second"));
        writeAsset("custom/b/B.xsd", schema(NS_B, "Note", "Text"));

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
    @DisplayName("xsd_reloadundan_once_eklenen_tur_istek_aninda_derlenir")
    void xsd_reloadundan_once_eklenen_tur_istek_aninda_derlenir() throws Exception {
        writeDefinitions(typeA("custom/a/A-first.xsd"));
        reloadAll();

        writeDefinitions(typeA("custom/a/A-first.xsd"), typeB());
        registry.reload(); // XSD bileşeni henüz çalışmadı

        assertThat(validator.validateCustom(doc(NS_B, "Note", "Text"), "TYPE_B", List.of(), null))
                .as("geçerli belge geçmeli").isEmpty();
        assertThat(validator.validateCustom(doc(NS_B, "Note", "Other"), "TYPE_B", List.of(), null))
                .as("geçersiz belge XSD hatası almalı, 'yüklenemedi' değil")
                .isNotEmpty()
                .allSatisfy(error -> assertThat(error).startsWith("Satır ").doesNotContain("yüklenemedi"));
    }

    @Test
    @DisplayName("xsd_yolu_degisen_tur_yeni_semayla_dogrulanir")
    void xsd_yolu_degisen_tur_yeni_semayla_dogrulanir() throws Exception {
        writeDefinitions(typeA("custom/a/A-first.xsd"));
        reloadAll();
        assertThat(validator.validateCustom(doc(NS_A, "Doc", "First"), "TYPE_A", List.of(), null)).isEmpty();

        writeDefinitions(typeA("custom/a/A-second.xsd"));
        registry.reload(); // XSD bileşeni henüz çalışmadı

        assertThat(validator.validateCustom(doc(NS_A, "Doc", "Second"), "TYPE_A", List.of(), null))
                .as("yeni şemaya uyan belge geçmeli").isEmpty();
        assertThat(validator.validateCustom(doc(NS_A, "Doc", "First"), "TYPE_A", List.of(), null))
                .as("eski şemaya uyan belge artık geçmemeli").isNotEmpty();

        validator.reload(); // anlık görüntü yolu

        assertThat(validator.validateCustom(doc(NS_A, "Doc", "Second"), "TYPE_A", List.of(), null)).isEmpty();
        assertThat(validator.validateCustom(doc(NS_A, "Doc", "First"), "TYPE_A", List.of(), null)).isNotEmpty();
    }

    @Test
    @DisplayName("derlenemeyen_tanim_her_istekte_yeniden_derlenmez")
    void derlenemeyen_tanim_her_istekte_yeniden_derlenmez() throws Exception {
        writeDefinitions(typeA("custom/a/A-first.xsd"));
        reloadAll();
        writeAsset("custom/b/B.xsd", "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><bozuk");

        writeDefinitions(typeA("custom/a/A-first.xsd"), typeB());
        registry.reload();

        assertThat(validator.validateCustom(doc(NS_B, "Note", "Text"), "TYPE_B", List.of(), null))
                .singleElement().asString().contains("TYPE_B özel belge türü için XSD şeması yüklenemedi");

        // Dosya düzelse de aynı tanım için sonuç bir sonraki XSD reload'una kadar saklanır.
        writeAsset("custom/b/B.xsd", schema(NS_B, "Note", "Text"));
        assertThat(validator.validateCustom(doc(NS_B, "Note", "Text"), "TYPE_B", List.of(), null))
                .as("başarısız derleme istek başına tekrarlanmamalı")
                .singleElement().asString().contains("yüklenemedi");

        validator.reload();
        assertThat(validator.validateCustom(doc(NS_B, "Note", "Text"), "TYPE_B", List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("reloadda_derlenemeyen_tanim_gercek_hatayi_korur")
    void reloadda_derlenemeyen_tanim_gercek_hatayi_korur() throws Exception {
        writeAsset("custom/b/B.xsd", "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><bozuk");
        writeDefinitions(typeB());
        reloadAll();

        // Tanım değişmedi: dosya düzelse de istek anında derleme yapılmaz, reload'un hatası geçerli.
        writeAsset("custom/b/B.xsd", schema(NS_B, "Note", "Text"));
        assertThat(validator.validateCustom(doc(NS_B, "Note", "Text"), "TYPE_B", List.of(), null))
                .singleElement().asString().contains("TYPE_B özel belge türü için XSD şeması yüklenemedi");
    }

    @Test
    @DisplayName("asiri_derin_sema_yalniz_kendisini_dusurur")
    void asiri_derin_sema_yalniz_kendisini_dusurur() throws Exception {
        writeAsset("custom/deep/Deep.xsd", deeplyNestedSchema(NS_B, 20_000));
        writeDefinitions(typeA("custom/a/A-first.xsd"), """
                  - id: TYPE_DEEP
                    root-namespace: "%s"
                    root-element: Level
                    xsd: custom/deep/Deep.xsd
                """.formatted(NS_B));

        ReloadResult result = onSmallStack(this::reloadAll);

        assertThat(String.join(" | ", result.errors()))
                .contains("TYPE_DEEP özel belge türü XSD şeması yüklenemedi")
                .contains("StackOverflowError")
                .doesNotContain("TYPE_A");
        assertThat(validator.validateCustom(doc(NS_A, "Doc", "First"), "TYPE_A", List.of(), null))
                .as("diğer özel tür derlenmiş olmalı").isEmpty();
    }

    @Test
    @DisplayName("asiri_derin_sema_istek_aninda_derlemede_de_yalniz_kendisini_dusurur")
    void asiri_derin_sema_istek_aninda_derlemede_de_yalniz_kendisini_dusurur() throws Exception {
        writeDefinitions(typeA("custom/a/A-first.xsd"));
        reloadAll();
        writeAsset("custom/deep/Deep.xsd", deeplyNestedSchema(NS_B, 20_000));
        writeDefinitions(typeA("custom/a/A-first.xsd"), """
                  - id: TYPE_DEEP
                    root-namespace: "%s"
                    root-element: Level
                    xsd: custom/deep/Deep.xsd
                """.formatted(NS_B));
        registry.reload();

        List<String> errors = onSmallStack(() ->
                validator.validateCustom(doc(NS_B, "Level", "x"), "TYPE_DEEP", List.of(), null));

        assertThat(errors).singleElement().asString().contains("TYPE_DEEP özel belge türü için XSD şeması yüklenemedi");
    }

    // ── Yardımcılar ─────────────────────────────────────────────────

    private ReloadResult reloadAll() {
        registry.reload();
        return validator.reload();
    }

    /** Derin özyinelemeyi makinenin yığın boyutundan bağımsız tetiklemek için küçük yığınlı thread. */
    private static <T> T onSmallStack(java.util.function.Supplier<T> action) throws InterruptedException {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        Thread thread = new Thread(null, () -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "small-stack", 256 * 1024);
        thread.start();
        thread.join(60_000);
        assertThat(failure.get()).as("hata çağırana sızmamalı").isNull();
        return result.get();
    }

    private static String schema(String ns, String root, String child) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="%s"
                           elementFormDefault="qualified">
                  <xs:element name="%s">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="%s" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """.formatted(ns, root, child);
    }

    private static String deeplyNestedSchema(String ns, int depth) {
        var sb = new StringBuilder("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"")
                .append(ns).append("\" elementFormDefault=\"qualified\">");
        for (int i = 0; i < depth; i++) {
            sb.append("<xs:element name=\"Level\"><xs:complexType><xs:sequence minOccurs=\"0\">");
        }
        for (int i = 0; i < depth; i++) {
            sb.append("</xs:sequence></xs:complexType></xs:element>");
        }
        return sb.append("</xs:schema>").toString();
    }

    private static byte[] doc(String ns, String root, String child) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<r:" + root + " xmlns:r=\"" + ns + "\">\n  <r:"
                + child + ">x</r:" + child + ">\n</r:" + root + ">\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String typeA(String xsd) {
        return """
                  - id: TYPE_A
                    root-namespace: "%s"
                    root-element: Doc
                    xsd: %s
                """.formatted(NS_A, xsd);
    }

    private static String typeB() {
        return """
                  - id: TYPE_B
                    root-namespace: "%s"
                    root-element: Note
                    xsd: custom/b/B.xsd
                """.formatted(NS_B);
    }

    private void writeDefinitions(String... entries) throws Exception {
        writeAsset(CustomDocumentTypeRegistry.ASSET_PATH, "document-types:\n" + String.join("", entries));
    }

    private void writeAsset(String relative, String content) throws Exception {
        Path target = assetDir.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
