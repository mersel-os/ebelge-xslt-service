package io.mersel.services.xslt.web;

import io.mersel.services.xslt.application.enums.DocumentType;
import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.DocumentTypeDetectionException;
import io.mersel.services.xslt.application.interfaces.IDocumentTypeDetector;
import io.mersel.services.xslt.application.interfaces.ISchemaValidator;
import io.mersel.services.xslt.application.interfaces.ISchematronValidator;
import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.SuppressionResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.mersel.services.xslt.web.controllers.ValidationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ValidationController birim testleri.
 * <p>
 * Otomatik belge türü tespiti ve doğrulama akışını test eder.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("POST /v1/validate")
class ValidationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IDocumentTypeDetector documentTypeDetector;

    @Mock
    private ISchemaValidator schemaValidator;

    @Mock
    private ISchematronValidator schematronValidator;

    @Mock
    private IValidationProfileService profileService;

    @Mock
    private XsltMetrics xsltMetrics;

    @InjectMocks
    private ValidationController validationController;

    @BeforeEach
    void setUp() throws Exception {
        // @Value alanlarını reflection ile set et (@InjectMocks bunları inject etmez)
        var sizeField = ValidationController.class.getDeclaredField("maxValidationSizeMb");
        sizeField.setAccessible(true);
        sizeField.setInt(validationController, 100);

        mockMvc = MockMvcBuilders.standaloneSetup(validationController).build();
    }

    @Test
    @DisplayName("Geçerli XML için başarılı yanıt ve tespit bilgisi dönmeli")
    void shouldReturnSuccessWithDetectionInfo() throws Exception {
        when(documentTypeDetector.detect(any(byte[].class)))
                .thenReturn(DocumentType.INVOICE);
        when(profileService.resolveXsdOverrides(isNull(), eq("INVOICE")))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), eq(SchemaValidationType.INVOICE), anyList()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), eq(SchematronValidationType.UBLTR_MAIN), any(), any()))
                .thenReturn(Collections.emptyList());
        when(profileService.applyXsdSuppressions(anyList(), isNull(), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        when(profileService.applySchematronSuppressions(anyList(), isNull(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(List.of(), List.of(), null, 0));

        var xmlFile = new MockMultipartFile("source", "test.xml", "text/xml",
                "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>".getBytes());

        mockMvc.perform(multipart("/v1/validate")
                        .file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(true))
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"))
                .andExpect(jsonPath("$.result.appliedXsd").value("UBL-Invoice-2.1.xsd"))
                .andExpect(jsonPath("$.result.appliedSchematron").value("UBLTR_MAIN"));
    }

    @Test
    @DisplayName("Geçersiz XML için hata listesi dönmeli")
    void shouldReturnErrorsForInvalidXml() throws Exception {
        var schematronErrors = List.of(
                new SchematronError("InvoiceIDCheck", "matches(cbc:ID,...)", "Schematron hatası 1")
        );

        when(documentTypeDetector.detect(any(byte[].class)))
                .thenReturn(DocumentType.INVOICE);
        when(profileService.resolveXsdOverrides(isNull(), eq("INVOICE")))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), eq(SchemaValidationType.INVOICE), anyList()))
                .thenReturn(List.of("Şema hatası 1", "Şema hatası 2"));
        when(schematronValidator.validate(any(), eq(SchematronValidationType.UBLTR_MAIN), any(), any()))
                .thenReturn(schematronErrors);
        when(profileService.applyXsdSuppressions(anyList(), isNull(), anyList(), anySet()))
                .thenReturn(List.of("Şema hatası 1", "Şema hatası 2"));
        when(profileService.applySchematronSuppressions(anyList(), isNull(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(schematronErrors, List.of(), null, 0));

        var xmlFile = new MockMultipartFile("source", "test.xml", "text/xml",
                "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>".getBytes());

        mockMvc.perform(multipart("/v1/validate")
                        .file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.validSchema").value(false))
                .andExpect(jsonPath("$.result.validSchematron").value(false))
                .andExpect(jsonPath("$.result.schemaValidationErrors.length()").value(2))
                .andExpect(jsonPath("$.result.schematronValidationErrors.length()").value(1));
    }

    @Test
    @DisplayName("Boş dosya için BadRequest dönmeli")
    void shouldReturnBadRequestForEmptyFile() throws Exception {
        var emptyFile = new MockMultipartFile("source", "empty.xml", "text/xml", new byte[0]);

        mockMvc.perform(multipart("/v1/validate")
                        .file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tespit edilemeyen belge türü için BadRequest dönmeli")
    void shouldReturnBadRequestForUndetectableDocument() throws Exception {
        when(documentTypeDetector.detect(any(byte[].class)))
                .thenThrow(new DocumentTypeDetectionException("Tanınmayan belge"));

        var xmlFile = new MockMultipartFile("source", "test.xml", "text/xml",
                "<Unknown/>".getBytes());

        mockMvc.perform(multipart("/v1/validate")
                        .file(xmlFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    @DisplayName("Profil ile doğrulamada bastırma bilgisi dönmeli")
    void shouldReturnSuppressionInfoWithProfile() throws Exception {
        var suppressedError = new SchematronError("XadesSignatureCheck", "ds:KeyInfo",
                "ds:KeyInfo elemani zorunlu bir elemandir.");
        var activeError = new SchematronError("UBLVersionIDCheck", "cbc:UBLVersionID = '2.1'",
                "Gecersiz UBL versiyon.");

        when(documentTypeDetector.detect(any(byte[].class)))
                .thenReturn(DocumentType.INVOICE);
        when(profileService.resolveXsdOverrides(eq("unsigned"), eq("INVOICE")))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), eq(SchemaValidationType.INVOICE), anyList()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), eq(SchematronValidationType.UBLTR_MAIN), any(), any()))
                .thenReturn(List.of(suppressedError, activeError));
        when(profileService.applyXsdSuppressions(anyList(), eq("unsigned"), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        when(profileService.applySchematronSuppressions(anyList(), eq("unsigned"), anyList(), anySet()))
                .thenReturn(new SuppressionResult(
                        List.of(activeError),
                        List.of(suppressedError),
                        "unsigned",
                        1
                ));

        var xmlFile = new MockMultipartFile("source", "test.xml", "text/xml",
                "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>".getBytes());

        mockMvc.perform(multipart("/v1/validate")
                        .file(xmlFile)
                        .param("profile", "unsigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.validSchema").value(true))
                .andExpect(jsonPath("$.result.validSchematron").value(false))
                .andExpect(jsonPath("$.result.detectedDocumentType").value("INVOICE"))
                .andExpect(jsonPath("$.result.schematronValidationErrors.length()").value(1))
                .andExpect(jsonPath("$.result.schematronValidationErrors[0].ruleId").value("UBLVersionIDCheck"))
                .andExpect(jsonPath("$.result.suppressionInfo.profile").value("unsigned"))
                .andExpect(jsonPath("$.result.suppressionInfo.suppressedCount").value(1))
                .andExpect(jsonPath("$.result.suppressionInfo.suppressedErrors.length()").value(1));
    }

    @Test
    @DisplayName("e-Defter belgesi için doğru tespit bilgisi dönmeli")
    void shouldDetectEDefterDocument() throws Exception {
        when(documentTypeDetector.detect(any(byte[].class)))
                .thenReturn(DocumentType.EDEFTER_YEVMIYE);
        when(profileService.resolveXsdOverrides(isNull(), eq("EDEFTER")))
                .thenReturn(Collections.emptyList());
        when(schemaValidator.validate(any(), eq(SchemaValidationType.EDEFTER), anyList()))
                .thenReturn(Collections.emptyList());
        when(schematronValidator.validate(any(), eq(SchematronValidationType.EDEFTER_YEVMIYE), any(), any()))
                .thenReturn(Collections.emptyList());
        when(profileService.applyXsdSuppressions(anyList(), isNull(), anyList(), anySet()))
                .thenReturn(Collections.emptyList());
        when(profileService.applySchematronSuppressions(anyList(), isNull(), anyList(), anySet()))
                .thenReturn(new SuppressionResult(List.of(), List.of(), null, 0));

        var xmlFile = new MockMultipartFile("source", "yevmiye.xml", "text/xml",
                "<edefter:defter xmlns:edefter=\"http://www.edefter.gov.tr\"/>".getBytes());

        mockMvc.perform(multipart("/v1/validate")
                        .file(xmlFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.detectedDocumentType").value("EDEFTER_YEVMIYE"))
                .andExpect(jsonPath("$.result.appliedXsd").value("edefter.xsd"))
                .andExpect(jsonPath("$.result.appliedSchematron").value("EDEFTER_YEVMIYE"))
                .andExpect(jsonPath("$.result.appliedXsdPath").value("validator/eledger/schema/edefter.xsd"))
                .andExpect(jsonPath("$.result.appliedSchematronPath").value("validator/eledger/schematron/edefter_yevmiye.sch"));
    }
}
