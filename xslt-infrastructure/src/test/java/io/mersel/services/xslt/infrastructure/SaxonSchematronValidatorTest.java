package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.SchematronCustomAssertion;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import net.sf.saxon.s9api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SaxonSchematronValidator birim testleri.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SaxonSchematronValidator")
class SaxonSchematronValidatorTest {

    @Mock
    private AssetManager assetManager;

    @Mock
    private SchematronRuntimeCompiler runtimeCompiler;

    @Mock
    private XsltMetrics metrics;

    private SaxonSchematronValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SaxonSchematronValidator(assetManager, runtimeCompiler, metrics);
        validator.init();
    }

    // ── Test 1 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("validate_derlenmis_schematron_yoksa_hata_mesaji — Derlenmiş schematron yoksa açıklayıcı hata dönmeli")
    void validate_derlenmis_schematron_yoksa_hata_mesaji() {
        byte[] source = "<Invoice/>".getBytes(StandardCharsets.UTF_8);

        List<SchematronError> errors = validator.validate(
                source, SchematronValidationType.UBLTR_MAIN, null, null);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message())
                .contains("UBLTR_MAIN")
                .contains("Schematron kuralları yüklenemedi");
        assertThat(errors.get(0).ruleId()).isNull();
        assertThat(errors.get(0).test()).isNull();

        verify(metrics).recordValidation(eq("schematron"), eq("UBLTR_MAIN"), eq("error"), anyLong());
    }

    // ── Test 2 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("validate_gecerli_xml_bos_hata_listesi — Geçerli XML için boş hata listesi dönmeli")
    void validate_gecerli_xml_bos_hata_listesi() throws Exception {
        // Basit bir identity transform XSLT oluştur — boş çıktı üretecek
        String xslt = """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <Result/>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltExecutable executable = compileXslt(xslt);
        injectCompiledSchematron(SchematronValidationType.UBLTR_MAIN, executable);

        byte[] source = "<Invoice><ID>INV001</ID></Invoice>".getBytes(StandardCharsets.UTF_8);

        List<SchematronError> errors = validator.validate(
                source, SchematronValidationType.UBLTR_MAIN, "efatura", null);

        assertThat(errors).isEmpty();
        verify(metrics).recordValidation(eq("schematron"), eq("UBLTR_MAIN"), eq("valid"), anyLong());
    }

    // ── Test 3 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("validate_gecersiz_xml_hata_doner — Geçersiz XML için SchematronError nesneleri dönmeli")
    void validate_gecersiz_xml_hata_doner() throws Exception {
        // Hata çıktısı üreten XSLT
        String xslt = """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <Errors>
                            <Error ruleId="BR-01" test="cbc:ID != ''">Fatura ID boş olamaz</Error>
                            <Error ruleId="BR-02" test="cac:AccountingSupplierParty">Tedarikçi zorunlu</Error>
                        </Errors>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltExecutable executable = compileXslt(xslt);
        injectCompiledSchematron(SchematronValidationType.UBLTR_MAIN, executable);

        byte[] source = "<Invoice/>".getBytes(StandardCharsets.UTF_8);

        List<SchematronError> errors = validator.validate(
                source, SchematronValidationType.UBLTR_MAIN, "efatura", null);

        assertThat(errors).hasSize(2);

        assertThat(errors.get(0).ruleId()).isEqualTo("BR-01");
        assertThat(errors.get(0).test()).isEqualTo("cbc:ID != ''");
        assertThat(errors.get(0).message()).isEqualTo("Fatura ID boş olamaz");

        assertThat(errors.get(1).ruleId()).isEqualTo("BR-02");
        assertThat(errors.get(1).test()).isEqualTo("cac:AccountingSupplierParty");
        assertThat(errors.get(1).message()).isEqualTo("Tedarikçi zorunlu");

        verify(metrics).recordValidation(eq("schematron"), eq("UBLTR_MAIN"), eq("invalid"), anyLong());
    }

    // ── Test 4 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("validate_ubltr_main_type_parametresi — UBLTR_MAIN için 'type' parametresi transformer'a geçmeli")
    void validate_ubltr_main_type_parametresi() throws Exception {
        // type parametresini çıktıya yazan XSLT
        String xslt = """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:param name="type" select="'efatura'"/>
                    <xsl:template match="/">
                        <Result>
                            <xsl:if test="$type = 'earchive'">
                                <Error ruleId="TYPE_CHECK" test="$type">Tip parametresi: <xsl:value-of select="$type"/></Error>
                            </xsl:if>
                        </Result>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltExecutable executable = compileXslt(xslt);
        injectCompiledSchematron(SchematronValidationType.UBLTR_MAIN, executable);

        byte[] source = "<Invoice/>".getBytes(StandardCharsets.UTF_8);

        // "earchive" parametresi ile çağır — Error üretecek
        List<SchematronError> errors = validator.validate(
                source, SchematronValidationType.UBLTR_MAIN, "earchive", null);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).ruleId()).isEqualTo("TYPE_CHECK");
        assertThat(errors.get(0).message()).contains("earchive");
    }

    // ── Test 5 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("reload_kaynak_yok_partial_doner — Asset dosyaları yoksa reload PARTIAL döndürmeli")
    void reload_kaynak_yok_partial_doner() {
        // Tüm assetExists çağrıları false dönecek
        when(assetManager.assetExists(anyString())).thenReturn(false);

        ReloadResult result = validator.reload();

        // Hiçbir asset bulunamadığında ve hiçbiri derlenemediğinde FAILED olmalı
        // çünkü newCache boş olacak
        assertThat(result.status()).isEqualTo(ReloadResult.Status.FAILED);
        assertThat(result.loadedCount()).isEqualTo(0);
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.componentName()).isEqualTo("Schematron Rules");
    }

    // ── Test 6 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("reload_basarili_tum_schema_yuklenir — Tüm asset'ler varsa reload OK ve doğru count dönmeli")
    void reload_basarili_tum_schema_yuklenir(@TempDir Path tempDir) throws Exception {
        // SOURCE_XML_MAP: UBLTR_MAIN → 1 dosya
        String ubltrMainPath = "validator/ubl-tr-package/schematron/UBL-TR_Main_Schematron.xml";
        when(assetManager.assetExists(ubltrMainPath)).thenReturn(true);
        Path ubltrFile = tempDir.resolve("ubltr_main.xml");
        java.nio.file.Files.writeString(ubltrFile, "<dummy/>");
        when(assetManager.resolveAssetOnDisk(ubltrMainPath)).thenReturn(ubltrFile);

        // Mock runtimeCompiler for SOURCE_XML
        XsltExecutable mockExec = compileXslt(
                "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'><xsl:template match='/'/></xsl:stylesheet>");
        when(runtimeCompiler.compileAndReturn(any(Path.class)))
                .thenReturn(new SchematronRuntimeCompiler.CompileResult(mockExec, new byte[0]));

        // SOURCE_SCH_MAP: 6 dosya (e-Defter)
        String[] schPaths = {
                "validator/eledger/schematron/edefter_yevmiye.sch",
                "validator/eledger/schematron/edefter_kebir.sch",
                "validator/eledger/schematron/edefter_berat.sch",
                "validator/eledger/schematron/edefter_rapor.sch",
                "validator/eledger/schematron/envanter_berat.sch",
                "validator/eledger/schematron/envanter_defter.sch"
        };
        for (String schPath : schPaths) {
            when(assetManager.assetExists(schPath)).thenReturn(true);
            Path schFile = tempDir.resolve(schPath.replace("/", "_"));
            java.nio.file.Files.writeString(schFile, "<dummy/>");
            when(assetManager.resolveAssetOnDisk(schPath)).thenReturn(schFile);
        }

        // PRECOMPILED_XSL_MAP: EARCHIVE_REPORT → 1 dosya
        String earsivPath = "validator/earchive/schematron/earsiv_schematron.xsl";
        when(assetManager.assetExists(earsivPath)).thenReturn(true);
        String simpleXsl = "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
                + "<xsl:template match='/'/></xsl:stylesheet>";
        when(assetManager.getAssetStream(earsivPath))
                .thenReturn(new ByteArrayInputStream(simpleXsl.getBytes(StandardCharsets.UTF_8)));

        ReloadResult result = validator.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.OK);
        // 1 (UBLTR_MAIN) + 6 (SCH) + 1 (EARCHIVE) = 8
        assertThat(result.loadedCount()).isEqualTo(8);
        assertThat(result.errors()).isEmpty();
        assertThat(result.componentName()).isEqualTo("Schematron Rules");
        assertThat(validator.getLoadedCount()).isEqualTo(8);
    }

    // ── Test 7 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("extractSchematronErrors_xxe_korunmali — XXE koruması external entity'leri engellemeli")
    void extractSchematronErrors_xxe_korunmali() throws Exception {
        // DOCTYPE ile external entity tanımı içeren XML
        String xxePayload = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <Errors>
                    <Error>&xxe;</Error>
                </Errors>
                """;

        // extractSchematronErrors private metoda reflection ile eriş
        Method method = SaxonSchematronValidator.class.getDeclaredMethod(
                "extractSchematronErrors", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<SchematronError> errors = (List<SchematronError>) method.invoke(validator, xxePayload);

        // XXE koruması: disallow-doctype-decl açık olduğu için parse başarısız olur.
        // extractSchematronErrors fallback'e düşer ve ham text'i tek SchematronError olarak döner
        assertThat(errors).isNotEmpty();
        // /etc/passwd içeriği OLMAMALI
        for (SchematronError error : errors) {
            assertThat(error.message()).doesNotContain("root:");
        }
    }

    // ── Test 8 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLoadedCount_bos_baslagicta — Başlangıçta loaded count 0 olmalı")
    void getLoadedCount_bos_baslagicta() {
        assertThat(validator.getLoadedCount()).isEqualTo(0);
    }

    // ── Test 9: Custom Rule Injection ────────────────────────────────────

    @Test
    @DisplayName("injectCustomRules — Özel kurallar Schematron XML'e enjekte edilmeli")
    void injectCustomRules_ozel_kurallar_enjekte_edilmeli() throws Exception {
        // Minimal Schematron XML
        String originalXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                    <sch:pattern id="test">
                        <sch:rule context="inv:Invoice">
                            <sch:assert test="cbc:ID">ID zorunlu</sch:assert>
                        </sch:rule>
                    </sch:pattern>
                </sch:schema>
                """;

        List<SchematronCustomAssertion> customRules = List.of(
                new SchematronCustomAssertion("inv:Invoice",
                        "cac:AccountingSupplierParty", "Satici zorunlu", "CUSTOM-001"),
                new SchematronCustomAssertion("inv:Invoice/cac:InvoiceLine",
                        "cbc:InvoicedQuantity > 0", "Miktar sifirdan buyuk olmali", "CUSTOM-002")
        );

        // injectCustomRules package-private, dolayısıyla doğrudan erişilebilir
        byte[] result = validator.injectCustomRules(
                originalXml.getBytes(StandardCharsets.UTF_8), customRules, "test-profile");

        String resultStr = new String(result, StandardCharsets.UTF_8);

        // Orijinal pattern hala var
        assertThat(resultStr).contains("id=\"test\"");

        // Özel kurallar enjekte edilmiş
        assertThat(resultStr).contains("custom-rules-test-profile");
        assertThat(resultStr).contains("CUSTOM-001");
        assertThat(resultStr).contains("CUSTOM-002");
        assertThat(resultStr).contains("Satici zorunlu");
        assertThat(resultStr).contains("Miktar sifirdan buyuk olmali");
        assertThat(resultStr).contains("cac:AccountingSupplierParty");
        assertThat(resultStr).contains("cbc:InvoicedQuantity");
    }

    // ── Test 10: Custom Rule Context Grouping ──────────────────────────

    @Test
    @DisplayName("injectCustomRules — Aynı context kurallar tek rule altında gruplanmalı")
    void injectCustomRules_ayni_context_tek_rule() throws Exception {
        String originalXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                </sch:schema>
                """;

        List<SchematronCustomAssertion> customRules = List.of(
                new SchematronCustomAssertion("inv:Invoice",
                        "cac:AccountingSupplierParty", "Satici zorunlu", "CUSTOM-001"),
                new SchematronCustomAssertion("inv:Invoice",
                        "cac:AccountingCustomerParty", "Alici zorunlu", "CUSTOM-002"),
                new SchematronCustomAssertion("inv:Invoice/cac:InvoiceLine",
                        "cbc:InvoicedQuantity > 0", "Miktar sifirdan buyuk", "CUSTOM-003")
        );

        byte[] result = validator.injectCustomRules(
                originalXml.getBytes(StandardCharsets.UTF_8), customRules, "test-profile");

        String resultStr = new String(result, StandardCharsets.UTF_8);

        // Tek pattern olmalı
        assertThat(resultStr).contains("custom-rules-test-profile");

        // İki farklı rule context olmalı: inv:Invoice ve inv:Invoice/cac:InvoiceLine
        // inv:Invoice context'inde 2 assert, InvoiceLine'da 1 assert
        assertThat(resultStr).contains("CUSTOM-001");
        assertThat(resultStr).contains("CUSTOM-002");
        assertThat(resultStr).contains("CUSTOM-003");
    }

    // ── Test 11: Custom Rule Empty List ──────────────────────────────────

    @Test
    @DisplayName("validate — Boş custom rules listesi ile standart doğrulama yapılmalı")
    void validate_bos_custom_rules_standart_dogrulama() throws Exception {
        String xslt = """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <Result/>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltExecutable executable = compileXslt(xslt);
        injectCompiledSchematron(SchematronValidationType.UBLTR_MAIN, executable);

        byte[] source = "<Invoice/>".getBytes(StandardCharsets.UTF_8);

        // Boş custom rules — standart validate yoluna düşmeli
        List<SchematronError> errors = validator.validate(
                source, SchematronValidationType.UBLTR_MAIN, "efatura", null,
                List.of(), null);

        assertThat(errors).isEmpty();
        verify(metrics).recordValidation(eq("schematron"), eq("UBLTR_MAIN"), eq("valid"), anyLong());
    }

    // ── Test 12: Custom Rule Invalid Fields Skipped ──────────────────────

    @Test
    @DisplayName("injectCustomRules — Eksik alanlı kurallar atlanmalı")
    void injectCustomRules_eksik_alanli_kurallar_atlanmali() throws Exception {
        String originalXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                </sch:schema>
                """;

        List<SchematronCustomAssertion> customRules = List.of(
                new SchematronCustomAssertion("inv:Invoice",
                        "cac:Signature", "Gecerli kural", "VALID"),
                new SchematronCustomAssertion("", "cac:Something", "Context bos", null),
                new SchematronCustomAssertion("inv:Invoice", "", "Test bos", null),
                new SchematronCustomAssertion("inv:Invoice", "cac:Test", "", null)
        );

        byte[] result = validator.injectCustomRules(
                originalXml.getBytes(StandardCharsets.UTF_8), customRules, "test-profile");

        String resultStr = new String(result, StandardCharsets.UTF_8);

        // Sadece geçerli kural enjekte edilmeli
        assertThat(resultStr).contains("VALID");
        assertThat(resultStr).contains("Gecerli kural");
        assertThat(resultStr).doesNotContain("Context bos");
        assertThat(resultStr).doesNotContain("Test bos");
    }

    // ── Test 13: invalidateCustomRuleCache ──────────────────────────────

    @Test
    @DisplayName("invalidateCustomRuleCache — Cache temizleme hata vermemeli")
    void invalidateCustomRuleCache_hata_vermemeli() throws Exception {
        // @PostConstruct'u simüle et — cache'i başlat
        var initMethod = SaxonSchematronValidator.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(validator);

        // Hata fırlatmamalı
        validator.invalidateCustomRuleCache();
    }

    // ── Test 14: Global Custom Rules ──────────────────────────────────

    @Test
    @DisplayName("setGlobalCustomRules — Global kurallar set ve get edilebilmeli")
    void setGlobalCustomRules_set_ve_get() {
        var rules = Map.of(
                SchematronValidationType.UBLTR_MAIN, List.of(
                        new SchematronCustomAssertion("inv:Invoice",
                                "not(starts-with(cbc:ID, 'GIB'))",
                                "Fatura ID GIB ile baslayamaz", "GLOBAL-001")
                )
        );

        validator.setGlobalCustomRules(rules);

        var result = validator.getGlobalCustomRules();
        assertThat(result).hasSize(1);
        assertThat(result).containsKey(SchematronValidationType.UBLTR_MAIN);
        assertThat(result.get(SchematronValidationType.UBLTR_MAIN)).hasSize(1);
        assertThat(result.get(SchematronValidationType.UBLTR_MAIN).get(0).id()).isEqualTo("GLOBAL-001");
    }

    // ── Test 15: Global Custom Rules Null Safety ────────────────────────

    @Test
    @DisplayName("setGlobalCustomRules — null parametre boş map dönmeli")
    void setGlobalCustomRules_null_bos_map() {
        validator.setGlobalCustomRules(null);

        var result = validator.getGlobalCustomRules();
        assertThat(result).isEmpty();
    }

    // ── Test 16: Global Custom Rules Default Empty ──────────────────────

    @Test
    @DisplayName("getGlobalCustomRules — Başlangıçta boş map dönmeli")
    void getGlobalCustomRules_baslangicta_bos() {
        var result = validator.getGlobalCustomRules();
        assertThat(result).isEmpty();
    }

    // ── Yardımcı Metotlar ────────────────────────────────────────────────

    /**
     * Verilen XSLT string'ini Saxon ile derler ve XsltExecutable döndürür.
     */
    private XsltExecutable compileXslt(String xsltString) throws SaxonApiException {
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();
        return compiler.compile(new StreamSource(
                new ByteArrayInputStream(xsltString.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * Reflection ile compiledSchematrons map'ine XsltExecutable ekler.
     */
    private void injectCompiledSchematron(SchematronValidationType type, XsltExecutable executable)
            throws Exception {
        Field field = SaxonSchematronValidator.class.getDeclaredField("compiledSchematrons");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<SchematronValidationType, XsltExecutable> currentMap =
                new HashMap<>((Map<SchematronValidationType, XsltExecutable>) field.get(validator));
        currentMap.put(type, executable);
        field.set(validator, Map.copyOf(currentMap));
    }
}
