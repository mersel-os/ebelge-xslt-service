package io.mersel.services.xslt.web.integration;

import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.ICustomDocumentTypeRegistry;
import io.mersel.services.xslt.application.interfaces.ISchemaValidator;
import io.mersel.services.xslt.application.interfaces.ISchematronValidator;
import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.SuppressionResult;
import io.mersel.services.xslt.infrastructure.DocumentTypeDetector;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.mersel.services.xslt.web.controllers.ValidationController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test — Gerçek XML dosyaları ile uctan uca doğrulama pipeline'ı.
 * <p>
 * {@link DocumentTypeDetector} gerçek SAX parser ile çalışır (new instance).
 * XSD/Schematron validatörleri mock'lanır (external asset dosyaları gerekmeden).
 * <p>
 * Bu test şunları doğrular:
 * <ul>
 *   <li>Multipart request parsing (MockMvc)</li>
 *   <li>Boyut doğrulama (size check)</li>
 *   <li>Otomatik belge türü tespiti (gerçek SAX parse)</li>
 *   <li>Controller iş akışı (detection → validation → response)</li>
 *   <li>JSON response formatı</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Validation Integration Tests")
class ValidationIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private ISchemaValidator schemaValidator;

    @Mock
    private ISchematronValidator schematronValidator;

    @Mock
    private IValidationProfileService profileService;

    @Mock
    private ICustomDocumentTypeRegistry customDocumentTypes;

    @Mock
    private XsltMetrics xsltMetrics;

    @BeforeEach
    void setUp() throws Exception {
        var controller = new ValidationController(
                new DocumentTypeDetector(),
                schemaValidator,
                schematronValidator,
                profileService,
                customDocumentTypes,
                xsltMetrics,
                new ObjectMapper()
        );

        // @Value alanını reflection ile set et
        Field sizeField = ValidationController.class.getDeclaredField("maxValidationSizeMb");
        sizeField.setAccessible(true);
        sizeField.setInt(controller, 100);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 1: UBL-TR e-Fatura
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UBL-TR Invoice — otomatik tespit edip INVOICE olarak doğrulamalı")
    void shouldDetectAndValidateUblTrInvoice() throws Exception {
        String invoiceXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
                    <cbc:CustomizationID>TR1.2</cbc:CustomizationID>
                    <cbc:ProfileID>TICARIFATURA</cbc:ProfileID>
                    <cbc:ID>INV2024000001</cbc:ID>
                    <cbc:IssueDate>2024-01-15</cbc:IssueDate>
                </Invoice>
                """;

        setupMocksForSuccessfulValidation();

        var xmlFile = new MockMultipartFile("source", "fatura.xml", "text/xml",
                invoiceXml.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"))
                .andExpect(jsonPath("$.result.appliedXsd").value("UBL-Invoice-2.1.xsd"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(true));
    }

    @Test
    @DisplayName("UBL-TR CreditNote — otomatik tespit edip CREDIT_NOTE olarak doğrulamalı")
    void shouldDetectAndValidateUblTrCreditNote() throws Exception {
        // e-Döviz, e-Dekont ve e-Gider Pusulası belgeleri de bu kökü kullanır.
        // Belge türü root element'ten belirlendiği için ProfileID'ye göre ayrı bir
        // yönlendirme yoktur.
        String creditNoteXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
                    <cbc:CustomizationID>TR1.2.1</cbc:CustomizationID>
                    <cbc:ProfileID>DOVIZALIM</cbc:ProfileID>
                    <cbc:ID>CN2024000001</cbc:ID>
                </CreditNote>
                """;

        setupMocksForSuccessfulValidation();

        var xmlFile = new MockMultipartFile("source", "credit-note.xml", "text/xml",
                creditNoteXml.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("CREDIT_NOTE"))
                .andExpect(jsonPath("$.result.appliedXsd").value("UBL-CreditNote-2.1.xsd"))
                .andExpect(jsonPath("$.result.appliedXsdPath")
                        .value("validator/ubl-tr-package/schema/maindoc/UBL-CreditNote-2.1.xsd"))
                .andExpect(jsonPath("$.result.appliedSchematron").value("UBLTR_MAIN"));

        verify(schemaValidator).validate(any(), eq(SchemaValidationType.CREDIT_NOTE), anyList(), any());
        verify(schematronValidator).validate(any(), eq(SchematronValidationType.UBLTR_MAIN),
                any(), anyList(), any(), anyMap());
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 2: e-Defter Yevmiye
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("e-Defter Yevmiye — otomatik tespit edip EDEFTER_YEVMIYE olarak doğrulamalı")
    void shouldDetectAndValidateEDefterYevmiye() throws Exception {
        String eDefterXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <edefter:defter xmlns:edefter="http://www.edefter.gov.tr"
                                xmlns:xbrli="http://www.xbrl.org/2003/instance">
                    <xbrli:context id="journal_context">
                        <xbrli:entity>
                            <xbrli:identifier scheme="http://www.edefter.gov.tr">1234567890</xbrli:identifier>
                        </xbrli:entity>
                        <xbrli:period>
                            <xbrli:startDate>2024-01-01</xbrli:startDate>
                            <xbrli:endDate>2024-01-31</xbrli:endDate>
                        </xbrli:period>
                    </xbrli:context>
                </edefter:defter>
                """;

        setupMocksForSuccessfulValidation();

        var xmlFile = new MockMultipartFile("source", "yevmiye.xml", "text/xml",
                eDefterXml.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("EDEFTER_YEVMIYE"))
                .andExpect(jsonPath("$.result.appliedSchematron").value("EDEFTER_YEVMIYE"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(true));
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 3: e-Envanter Defter
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("e-Envanter Defter — otomatik tespit edip ENVANTER_DEFTER olarak doğrulamalı")
    void shouldDetectAndValidateEnvanterDefter() throws Exception {
        String envanterXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <envanter:defter xmlns:envanter="http://www.edefter.gov.tr"
                                 xmlns:xbrli="http://www.xbrl.org/2003/instance">
                    <xbrli:context id="assets_context">
                        <xbrli:entity>
                            <xbrli:identifier scheme="http://www.edefter.gov.tr">1234567890</xbrli:identifier>
                        </xbrli:entity>
                    </xbrli:context>
                </envanter:defter>
                """;

        setupMocksForSuccessfulValidation();

        var xmlFile = new MockMultipartFile("source", "envanter.xml", "text/xml",
                envanterXml.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("ENVANTER_DEFTER"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(true));
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 4: Boş dosya → BadRequest
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Boş XML dosyası — 400 BadRequest dönmeli")
    void shouldReturnBadRequestForEmptyFile() throws Exception {
        var emptyFile = new MockMultipartFile("source", "empty.xml", "text/xml", new byte[0]);

        mockMvc.perform(multipart("/v1/validate").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 5: Tanınmayan belge türü → BadRequest
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Tanınmayan XML namespace — 400 BadRequest dönmeli")
    void shouldReturnBadRequestForUnknownDocumentType() throws Exception {
        String unknownXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <UnknownDocument xmlns="http://example.com/unknown">
                    <content>test</content>
                </UnknownDocument>
                """;

        var xmlFile = new MockMultipartFile("source", "unknown.xml", "text/xml",
                unknownXml.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 5b: custom-document-types.yml ile tanımlanmış kök
    // ────────────────────────────────────────────────────────────────────

    private static final String SAMPLE_VOUCHER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <v:Voucher xmlns:v="urn:example:sample:voucher-1">
                <v:ID>SMP-0001</v:ID>
            </v:Voucher>
            """;

    @Test
    @DisplayName("Özel belge türü — tanımdaki XSD ve Schematron ile doğrulanmalı")
    void shouldValidateCustomDocumentTypeWithConfiguredSchematron() throws Exception {
        when(customDocumentTypes.findByRoot("urn:example:sample:voucher-1", "Voucher"))
                .thenReturn(java.util.Optional.of(new io.mersel.services.xslt.application.models.CustomDocumentType(
                        "SAMPLE_VOUCHER", null, "urn:example:sample:voucher-1", "Voucher",
                        "custom/sample/maindoc/Sample-Voucher-1.0.xsd", SchematronValidationType.UBLTR_MAIN)));
        when(profileService.resolveXsdOverrides(any(), eq("SAMPLE_VOUCHER"))).thenReturn(List.of());
        when(profileService.resolveSchematronRules(any(), eq("UBLTR_MAIN"))).thenReturn(List.of());
        when(schemaValidator.validateCustom(any(), eq("SAMPLE_VOUCHER"), anyList(), any())).thenReturn(List.of());
        when(schematronValidator.validate(any(), eq(SchematronValidationType.UBLTR_MAIN), any(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(profileService.applyXsdSuppressions(anyList(), any(), anyList(),
                eq(new java.util.LinkedHashSet<>(List.of("SAMPLE_VOUCHER", "UBLTR_MAIN")))))
                .thenReturn(List.of());
        when(profileService.applySchematronSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(List.of(), List.of(), null, 0));

        var xmlFile = new MockMultipartFile("source", "sample.xml", "text/xml", SAMPLE_VOUCHER_XML.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("SAMPLE_VOUCHER"))
                .andExpect(jsonPath("$.result.appliedXsd").value("Sample-Voucher-1.0.xsd"))
                .andExpect(jsonPath("$.result.appliedXsdPath").value("custom/sample/maindoc/Sample-Voucher-1.0.xsd"))
                .andExpect(jsonPath("$.result.appliedSchematron").value("UBLTR_MAIN"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(true));

        verify(schemaValidator).validateCustom(any(), eq("SAMPLE_VOUCHER"), anyList(), any());
        verify(schemaValidator, org.mockito.Mockito.never()).validate(any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Özel belge türü Schematron'suz — yalnız XSD sonucu belirleyici")
    void shouldSkipSchematronForCustomDocumentTypeWithoutSchematron() throws Exception {
        when(customDocumentTypes.findByRoot("urn:example:sample:voucher-1", "Voucher"))
                .thenReturn(java.util.Optional.of(new io.mersel.services.xslt.application.models.CustomDocumentType(
                        "SAMPLE_VOUCHER", null, "urn:example:sample:voucher-1", "Voucher",
                        "custom/sample/maindoc/Sample-Voucher-1.0.xsd", null)));
        when(profileService.resolveXsdOverrides(any(), eq("SAMPLE_VOUCHER"))).thenReturn(List.of());
        List<String> xsdErrors = List.of("Satır 3, Sütun 5: \"Voucher\" elementinin içeriği eksik.");
        when(schemaValidator.validateCustom(any(), eq("SAMPLE_VOUCHER"), anyList(), any())).thenReturn(xsdErrors);
        when(profileService.applyXsdSuppressions(eq(xsdErrors), any(), anyList(), anySet())).thenReturn(xsdErrors);

        var xmlFile = new MockMultipartFile("source", "sample.xml", "text/xml", SAMPLE_VOUCHER_XML.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("SAMPLE_VOUCHER"))
                .andExpect(jsonPath("$.result.appliedSchematron").doesNotExist())
                .andExpect(jsonPath("$.result.validSchema").value(false))
                .andExpect(jsonPath("$.result.schemaValidationErrors[0]").value(xsdErrors.get(0)))
                .andExpect(jsonPath("$.result.validSchematron").value(true));

        org.mockito.Mockito.verifyNoInteractions(schematronValidator);
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 6: Doğrulama hatalı belge — hata listesi dönmeli
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Şema ve Schematron hataları olan fatura — hata detayları dönmeli")
    void shouldReturnValidationErrorsForInvalidInvoice() throws Exception {
        String invoiceXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">
                    <ID>TEST001</ID>
                </Invoice>
                """;

        var xsdErrors = List.of(
                "cvc-complex-type.2.4.b: Element 'ID' is not allowed",
                "cvc-complex-type.2.4.a: Missing child element 'UBLVersionID'"
        );
        var schematronErrors = List.of(
                new SchematronError("InvoiceIDCheck", "cbc:ID", "Fatura ID formatı geçersiz")
        );

        when(profileService.resolveXsdOverrides(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(profileService.resolveSchematronRules(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), any(), anyList(), any()))
                .thenReturn(xsdErrors);
        when(schematronValidator.validate(any(), any(), any(), anyList(), any(), any()))
                .thenReturn(schematronErrors);
        when(profileService.applyXsdSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(xsdErrors);
        when(profileService.applySchematronSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(schematronErrors, List.of(), null, 0));

        var xmlFile = new MockMultipartFile("source", "invalid-fatura.xml", "text/xml",
                invoiceXml.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.validSchema").value(false))
                .andExpect(jsonPath("$.result.validSchematron").value(false))
                .andExpect(jsonPath("$.result.schemaValidationErrors.length()").value(2))
                .andExpect(jsonPath("$.result.schematronValidationErrors.length()").value(1))
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"));
    }

    // ════════════════════════════════════════════════════════════════════
    // Gerçek UBL-TR Fatura (IDIS_Fatura.xml) ile Senaryolar
    // ════════════════════════════════════════════════════════════════════

    /**
     * Gerçek GİB IDIS fatura XML'ini test resources'dan yükler.
     */
    private byte[] loadRealInvoice() throws Exception {
        try (var is = getClass().getResourceAsStream("/fixtures/IDIS_Fatura.xml")) {
            if (is == null) {
                throw new IllegalStateException("Test fixture bulunamadı: /fixtures/IDIS_Fatura.xml");
            }
            return is.readAllBytes();
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 7: Gerçek GİB faturası — başarılı doğrulama
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gerçek GİB IDIS faturası — INVOICE olarak tespit edilmeli, doğrulama başarılı")
    void shouldValidateRealGibInvoiceSuccessfully() throws Exception {
        byte[] realInvoice = loadRealInvoice();

        setupMocksForSuccessfulValidation();

        var xmlFile = new MockMultipartFile("source", "IDIS_Fatura.xml", "text/xml", realInvoice);

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"))
                .andExpect(jsonPath("$.result.appliedXsd").value("UBL-Invoice-2.1.xsd"))
                .andExpect(jsonPath("$.result.appliedSchematron").value("UBLTR_MAIN"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(true))
                .andExpect(jsonPath("$.result.schemaValidationErrors.length()").value(0))
                .andExpect(jsonPath("$.result.schematronValidationErrors.length()").value(0))
                .andExpect(jsonPath("$.result.suppressionInfo").doesNotExist());
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 8: Gerçek fatura + cbc:ID Schematron hatası
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gerçek GİB faturası + cbc:ID hatası — Schematron hata listesinde InvoiceIDCheck dönmeli")
    void shouldReturnSchematronErrorForBrokenInvoiceId() throws Exception {
        byte[] realInvoice = loadRealInvoice();

        // cbc:ID formatını bozulmuş gibi davran — validator hata döner
        var invoiceIdError = new SchematronError(
                "InvoiceIDCheck",
                "matches(cbc:ID, '^[A-Z]{3}[0-9]{4}[0-9]{9}$')",
                "cbc:ID elemanı 'XXX' isimli serinin formatına uymuyor. " +
                        "Beklenen format: ABC202400000001"
        );
        var schematronErrors = List.of(invoiceIdError);

        when(profileService.resolveXsdOverrides(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(profileService.resolveSchematronRules(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), any(), anyList(), any()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), any(), any(), anyList(), any(), any()))
                .thenReturn(schematronErrors);
        when(profileService.applyXsdSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        when(profileService.applySchematronSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(schematronErrors, List.of(), null, 0));

        var xmlFile = new MockMultipartFile("source", "IDIS_Fatura.xml", "text/xml", realInvoice);

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(false))
                .andExpect(jsonPath("$.result.schematronValidationErrors.length()").value(1))
                .andExpect(jsonPath("$.result.schematronValidationErrors[0].ruleId").value("InvoiceIDCheck"))
                .andExpect(jsonPath("$.result.schematronValidationErrors[0].test")
                        .value("matches(cbc:ID, '^[A-Z]{3}[0-9]{4}[0-9]{9}$')"));
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 9: Gerçek fatura + cbc:ID hatası + suppression profili
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gerçek GİB faturası + cbc:ID hatası + suppression — hata bastırılmalı")
    void shouldSuppressInvoiceIdErrorWithProfile() throws Exception {
        byte[] realInvoice = loadRealInvoice();

        var invoiceIdError = new SchematronError(
                "InvoiceIDCheck",
                "matches(cbc:ID, '^[A-Z]{3}[0-9]{4}[0-9]{9}$')",
                "cbc:ID elemanı 'XXX' isimli serinin formatına uymuyor."
        );
        var schematronErrors = List.of(invoiceIdError);

        when(profileService.resolveXsdOverrides(eq("test-suppress"), anyString()))
                .thenReturn(Collections.emptyList());
        when(profileService.resolveSchematronRules(eq("test-suppress"), anyString()))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), any(), anyList(), any()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), any(), any(), anyList(), any(), any()))
                .thenReturn(schematronErrors);
        when(profileService.applyXsdSuppressions(anyList(), eq("test-suppress"), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        // Profil InvoiceIDCheck hatasını bastırıyor
        when(profileService.applySchematronSuppressions(anyList(), eq("test-suppress"), anyList(), anySet()))
                .thenReturn(new SuppressionResult(
                        List.of(),             // activeErrors: boş (hata bastırıldı)
                        schematronErrors,      // suppressedErrors: InvoiceIDCheck
                        "test-suppress",       // profileName
                        1                      // suppressedCount
                ));

        var xmlFile = new MockMultipartFile("source", "IDIS_Fatura.xml", "text/xml", realInvoice);

        mockMvc.perform(multipart("/v1/validate")
                        .file(xmlFile)
                        .param("profile", "test-suppress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(true))
                .andExpect(jsonPath("$.result.schematronValidationErrors.length()").value(0))
                // Bastırma bilgileri dönmeli
                .andExpect(jsonPath("$.result.suppressionInfo").exists())
                .andExpect(jsonPath("$.result.suppressionInfo.profile").value("test-suppress"))
                .andExpect(jsonPath("$.result.suppressionInfo.suppressedCount").value(1))
                .andExpect(jsonPath("$.result.suppressionInfo.suppressedErrors.length()").value(1))
                .andExpect(jsonPath("$.result.suppressionInfo.suppressedErrors[0].ruleId")
                        .value("InvoiceIDCheck"));
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo 10: Gerçek fatura + birden fazla hata + kısmi suppression
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gerçek GİB faturası + 2 Schematron hatası + sadece biri bastırılmalı")
    void shouldPartiallySuppress_OnlyInvoiceIdError() throws Exception {
        byte[] realInvoice = loadRealInvoice();

        var invoiceIdError = new SchematronError(
                "InvoiceIDCheck",
                "matches(cbc:ID, '^[A-Z]{3}[0-9]{4}[0-9]{9}$')",
                "cbc:ID formatı geçersiz"
        );
        var versionError = new SchematronError(
                "UBLVersionIDCheck",
                "cbc:UBLVersionID = '2.1'",
                "UBL versiyonu 2.1 olmalıdır"
        );
        var allErrors = List.of(invoiceIdError, versionError);

        when(profileService.resolveXsdOverrides(eq("only-id-suppress"), anyString()))
                .thenReturn(Collections.emptyList());
        when(profileService.resolveSchematronRules(eq("only-id-suppress"), anyString()))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), any(), anyList(), any()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), any(), any(), anyList(), any(), any()))
                .thenReturn(allErrors);
        when(profileService.applyXsdSuppressions(anyList(), eq("only-id-suppress"), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        // Sadece InvoiceIDCheck bastırılıyor, UBLVersionIDCheck aktif kalıyor
        when(profileService.applySchematronSuppressions(anyList(), eq("only-id-suppress"), anyList(), anySet()))
                .thenReturn(new SuppressionResult(
                        List.of(versionError),      // activeErrors: UBLVersionIDCheck aktif
                        List.of(invoiceIdError),     // suppressedErrors: InvoiceIDCheck bastırıldı
                        "only-id-suppress",
                        1
                ));

        var xmlFile = new MockMultipartFile("source", "IDIS_Fatura.xml", "text/xml", realInvoice);

        mockMvc.perform(multipart("/v1/validate")
                        .file(xmlFile)
                        .param("profile", "only-id-suppress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"))
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(false))
                // Aktif kalan hata
                .andExpect(jsonPath("$.result.schematronValidationErrors.length()").value(1))
                .andExpect(jsonPath("$.result.schematronValidationErrors[0].ruleId")
                        .value("UBLVersionIDCheck"))
                // Bastırma bilgileri
                .andExpect(jsonPath("$.result.suppressionInfo.profile").value("only-id-suppress"))
                .andExpect(jsonPath("$.result.suppressionInfo.suppressedCount").value(1))
                .andExpect(jsonPath("$.result.suppressionInfo.suppressedErrors[0].ruleId")
                        .value("InvoiceIDCheck"));
    }

    // ────────────────────────────────────────────────────────────────────
    // Senaryo: DOCTYPE'lı belge (XXE) → tespit aşamasında BadRequest
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Harici varlık içeren DOCTYPE — 400 dönmeli, XSD ve Schematron'a hiç ulaşmamalı")
    void shouldRejectDoctypeBeforeSchemaAndSchematron() throws Exception {
        String xxeInvoice = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Invoice [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>&xxe;</cbc:ID>
                </Invoice>
                """;

        var xmlFile = new MockMultipartFile("source", "fatura.xml", "text/xml",
                xxeInvoice.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage").value(
                        org.hamcrest.Matchers.containsString("DOCTYPE")));

        verifyNoInteractions(schemaValidator, schematronValidator);
    }

    // ────────────────────────────────────────────────────────────────────
    // Helper: Başarılı doğrulama senaryosu için tüm mock'ları kur
    // ────────────────────────────────────────────────────────────────────

    private void setupMocksForSuccessfulValidation() throws Exception {
        when(profileService.resolveXsdOverrides(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(profileService.resolveSchematronRules(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), any(), anyList(), any()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), any(), any(), anyList(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(profileService.applyXsdSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        when(profileService.applySchematronSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(List.of(), List.of(), null, 0));
    }
}
