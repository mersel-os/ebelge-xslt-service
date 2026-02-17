package io.mersel.services.xslt.web.integration;

import io.mersel.services.xslt.application.interfaces.ISchemaValidator;
import io.mersel.services.xslt.application.interfaces.ISchematronValidator;
import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.SuppressionResult;
import io.mersel.services.xslt.infrastructure.DocumentTypeDetector;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.mersel.services.xslt.web.controllers.ValidationController;
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
    private XsltMetrics xsltMetrics;

    @BeforeEach
    void setUp() throws Exception {
        var controller = new ValidationController(
                new DocumentTypeDetector(),
                schemaValidator,
                schematronValidator,
                profileService,
                xsltMetrics
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
        String creditNoteXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2">
                    <ID>CN2024000001</ID>
                </CreditNote>
                """;

        setupMocksForSuccessfulValidation();

        var xmlFile = new MockMultipartFile("source", "credit-note.xml", "text/xml",
                creditNoteXml.getBytes());

        mockMvc.perform(multipart("/v1/validate").file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("CREDIT_NOTE"));
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
        when(schematronValidator.validate(any(), any(), any(), any(), anyList(), any()))
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
        when(schematronValidator.validate(any(), any(), any(), any(), anyList(), any()))
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
        when(schematronValidator.validate(any(), any(), any(), any(), anyList(), any()))
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
        when(schematronValidator.validate(any(), any(), any(), any(), anyList(), any()))
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
    // Helper: Başarılı doğrulama senaryosu için tüm mock'ları kur
    // ────────────────────────────────────────────────────────────────────

    private void setupMocksForSuccessfulValidation() throws Exception {
        when(profileService.resolveXsdOverrides(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(profileService.resolveSchematronRules(any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), any(), anyList(), any()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), any(), any(), any(), anyList(), any()))
                .thenReturn(Collections.emptyList());
        when(profileService.applyXsdSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        when(profileService.applySchematronSuppressions(anyList(), any(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(List.of(), List.of(), null, 0));
    }
}
