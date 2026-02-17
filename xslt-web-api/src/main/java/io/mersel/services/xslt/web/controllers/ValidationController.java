package io.mersel.services.xslt.web.controllers;

import io.mersel.services.xslt.application.enums.DocumentType;
import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.DocumentTypeDetectionException;
import io.mersel.services.xslt.application.interfaces.IDocumentTypeDetector;
import io.mersel.services.xslt.application.interfaces.ISchemaValidator;
import io.mersel.services.xslt.application.interfaces.ISchematronValidator;
import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.models.DocumentTypeMapping;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.SuppressionResult;
import io.mersel.services.xslt.application.models.ValidationResponse;
import io.mersel.services.xslt.application.models.XsdOverride;
import io.mersel.services.xslt.application.models.XsltServiceResponse;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.mersel.services.xslt.web.dto.ValidationRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/**
 * XML doğrulama endpoint'i.
 * <p>
 * XML belge türünü otomatik tespit eder ve uygun XSD + Schematron doğrulaması uygular.
 * Tek zorunlu parametre: XML belgesi ({@code source}).
 * <p>
 * Profil tabanlı bastırma (suppression) desteği ile belirli doğrulama hatalarının
 * filtrelenmesini sağlar.
 */
@RestController
@RequestMapping("/v1")
@Tag(name = "Validation", description = "XML Schema ve Schematron doğrulama işlemleri")
public class ValidationController {

    private static final Logger log = LoggerFactory.getLogger(ValidationController.class);

    @Value("${xslt.limits.max-validation-size-mb:${XSLT_MAX_VALIDATION_SIZE_MB:100}}")
    private int maxValidationSizeMb;

    private final IDocumentTypeDetector documentTypeDetector;
    private final ISchemaValidator schemaValidator;
    private final ISchematronValidator schematronValidator;
    private final IValidationProfileService profileService;
    private final XsltMetrics xsltMetrics;

    public ValidationController(IDocumentTypeDetector documentTypeDetector,
                                ISchemaValidator schemaValidator,
                                ISchematronValidator schematronValidator,
                                IValidationProfileService profileService,
                                XsltMetrics xsltMetrics) {
        this.documentTypeDetector = documentTypeDetector;
        this.schemaValidator = schemaValidator;
        this.schematronValidator = schematronValidator;
        this.profileService = profileService;
        this.xsltMetrics = xsltMetrics;
    }

    @Operation(
            summary = "XML Doğrulama (Otomatik Tespit)",
            description = """
                    XML belgesinin türünü otomatik tespit eder ve uygun Schema (XSD) + Schematron kurallarına göre doğrular.
                    
                    **Desteklenen Belge Türleri:**
                    - UBL-TR: Invoice, CreditNote, DespatchAdvice, ReceiptAdvice, ApplicationResponse
                    - e-Arşiv: EArchive Report
                    - e-Defter: Yevmiye, Kebir, Berat, Rapor
                    - e-Envanter: Defter, Berat
                    
                    **Otomatik Tespit:** Belge türü XML namespace, root element ve e-Defter belgelerinde xbrli:context id bilgisinden tespit edilir.
                    
                    **Profil Desteği:** `profile` parametresi ile önceden tanımlı bastırma profili uygulanabilir.
                    """
    )
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<XsltServiceResponse<ValidationResponse>> validate(
            @ModelAttribute @Valid ValidationRequestDto requestDto) throws IOException {

        // ── Input doğrulama ──
        if (requestDto.getSource() == null || requestDto.getSource().isEmpty()) {
            return ResponseEntity.badRequest().body(XsltServiceResponse.error("XML belgesi boş olamaz"));
        }
        if (requestDto.getSource().getSize() > maxValidationSizeMb * 1024L * 1024L) {
            return ResponseEntity.badRequest().body(XsltServiceResponse.error(
                    "Belge boyutu çok büyük: " + (requestDto.getSource().getSize() / (1024 * 1024))
                            + " MB. Maksimum izin verilen: " + maxValidationSizeMb + " MB"));
        }

        byte[] source = requestDto.getSource().getBytes();
        var response = new ValidationResponse();

        // ── Belge türü tespiti ──
        DocumentType documentType;
        try {
            documentType = documentTypeDetector.detect(source);
        } catch (DocumentTypeDetectionException e) {
            log.warn("Belge türü tespit edilemedi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(XsltServiceResponse.error(
                    "Belge türü tespit edilemedi: " + e.getMessage()));
        }

        // Eşleştirme tablosundan XSD ve Schematron tiplerini çöz
        SchemaValidationType schemaType = DocumentTypeMapping.SCHEMA_MAP.get(documentType);
        SchematronValidationType schematronType = DocumentTypeMapping.SCHEMATRON_MAP.get(documentType);

        if (schemaType == null || schematronType == null) {
            return ResponseEntity.badRequest().body(XsltServiceResponse.error(
                    "Tespit edilen belge türü için doğrulama eşleştirmesi bulunamadı: " + documentType));
        }

        // ── Tespit bilgilerini response'a yaz ──
        response.setDetectedDocumentType(documentType.name());
        response.setAppliedXsd(DocumentTypeMapping.getXsdFileName(documentType));
        response.setAppliedXsdPath(DocumentTypeMapping.XSD_PATH_MAP.get(documentType));
        response.setAppliedSchematron(schematronType.name());
        response.setAppliedSchematronPath(DocumentTypeMapping.SCHEMATRON_PATH_MAP.get(schematronType));

        log.info("Doğrulama isteği — Tespit: {}, XSD: {}, SCH: {}, Profil: {}",
                documentType, schemaType, schematronType, requestDto.getProfile());

        // Metrics
        xsltMetrics.recordValidation(schemaType.name(), schematronType.name());

        // Ad-hoc bastırma kurallarını parse et
        List<String> additionalSuppressions = parseSuppressions(requestDto.getSuppressions());
        String profileName = requestDto.getProfile();

        // Aktif doğrulama tiplerini topla (scope filtresi için)
        Set<String> activeTypes = new LinkedHashSet<>();
        activeTypes.add(schemaType.name());
        activeTypes.add(schematronType.name());

        // ── Schema (XSD) doğrulama ──
        try {
            // Profil bazlı XSD override'larını çözümle
            List<XsdOverride> xsdOverrides = profileService.resolveXsdOverrides(
                    profileName, schemaType.name());

            List<String> schemaErrors = schemaValidator.validate(source, schemaType, xsdOverrides, profileName);

            // XSD bastırma uygula (scope-aware)
            List<String> activeSchemaErrors = profileService.applyXsdSuppressions(
                    schemaErrors, profileName, additionalSuppressions, activeTypes);
            response.setSchemaValidationErrors(activeSchemaErrors);
            response.setValidSchema(activeSchemaErrors.isEmpty());
        } catch (Exception e) {
            response.setValidSchema(false);
            response.setSchemaValidationErrors(List.of("XSD doğrulama hatası: " + e.getMessage()));
        }

        // ── Schematron doğrulama ──
        try {
            // Orijinal dosya adı — e-Defter Schematron base-uri() kontrolü için gerekli
            String sourceFileName = requestDto.getSource().getOriginalFilename();

            List<SchematronError> rawSchematronErrors = schematronValidator.validate(
                    source, schematronType, requestDto.getUblTrMainSchematronType(), sourceFileName);

            // Schematron bastırma uygula (scope-aware)
            SuppressionResult suppressionResult = profileService.applySchematronSuppressions(
                    rawSchematronErrors, profileName, additionalSuppressions, activeTypes);

            response.setSchematronValidationErrors(suppressionResult.activeErrors());
            response.setValidSchematron(suppressionResult.activeErrors().isEmpty());

            // Bastırma bilgisi — profil veya ek kurallar uygulandıysa ekle
            if ((profileName != null && !profileName.isBlank()) || !additionalSuppressions.isEmpty()) {
                response.setSuppressionInfo(buildSuppressionInfo(suppressionResult, rawSchematronErrors.size()));
            }

        } catch (Exception e) {
            response.setValidSchematron(false);
            response.setSchematronValidationErrors(List.of(
                    new SchematronError(null, null, "Schematron doğrulama hatası: " + e.getMessage())));
        }

        log.info("Doğrulama tamamlandı — Tür: {}, Schema: {}, Schematron: {}{}",
                documentType,
                response.isValidSchema() ? "Geçerli" : "Geçersiz",
                response.isValidSchematron() ? "Geçerli" : "Geçersiz",
                profileName != null ? " (profil: " + profileName + ")" : "");

        int schemaErrors = response.getSchemaValidationErrors() != null ? response.getSchemaValidationErrors().size() : 0;
        int schematronErrors = response.getSchematronValidationErrors() != null ? response.getSchematronValidationErrors().size() : 0;
        xsltMetrics.recordValidationErrors(schemaErrors, schematronErrors);

        // Belge tipi bazlı doğrulama dağılımı
        boolean overallValid = response.isValidSchema() && response.isValidSchematron();
        xsltMetrics.recordDocumentTypeValidation(documentType.name(), overallValid);

        // Profil kullanım metrikleri
        if (profileName != null && !profileName.isBlank()) {
            int suppressedCount = response.getSuppressionInfo() != null
                    ? ((Number) response.getSuppressionInfo().getOrDefault("suppressedCount", 0)).intValue()
                    : 0;
            xsltMetrics.recordProfileUsage(profileName, suppressedCount);
        }

        return ResponseEntity.ok(XsltServiceResponse.success(response));
    }

    /**
     * Virgülle ayrılmış bastırma kurallarını parse eder.
     */
    private List<String> parseSuppressions(String suppressions) {
        if (suppressions == null || suppressions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(suppressions.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .filter(s -> s.length() <= 500) // Kural uzunluk sınırı (XPath test ifadeleri uzun olabilir)
                .toList();
    }

    /**
     * Bastırma bilgisi haritasını oluşturur.
     */
    private Map<String, Object> buildSuppressionInfo(SuppressionResult result, int totalRawErrors) {
        var info = new LinkedHashMap<String, Object>();
        info.put("profile", result.profileName());
        info.put("totalRawErrors", totalRawErrors);
        info.put("suppressedCount", result.suppressedCount());
        info.put("suppressedErrors", result.suppressedErrors());
        return info;
    }
}
