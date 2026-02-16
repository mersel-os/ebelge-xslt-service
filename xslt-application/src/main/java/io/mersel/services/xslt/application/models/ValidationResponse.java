package io.mersel.services.xslt.application.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XML doğrulama sonuç modeli.
 * <p>
 * Hem Schema (XSD) hem Schematron doğrulama sonuçlarını içerir.
 * Otomatik tespit edilen belge türü ve uygulanan XSD/Schematron bilgileri
 * {@code detectedDocumentType}, {@code appliedXsd}, {@code appliedSchematron},
 * {@code appliedXsdPath}, {@code appliedSchematronPath} alanlarında sunulur.
 * <p>
 * Profil tabanlı bastırma (suppression) uygulandığında,
 * {@code suppressionInfo} alanı bastırma detaylarını taşır.
 */
public class ValidationResponse {

    // ── Tespit bilgileri ──
    private String detectedDocumentType;
    private String appliedXsd;
    private String appliedSchematron;
    private String appliedXsdPath;
    private String appliedSchematronPath;

    // ── Doğrulama sonuçları ──
    private boolean validSchema;
    private boolean validSchematron;
    private List<String> schemaValidationErrors = new ArrayList<>();
    private List<SchematronError> schematronValidationErrors = new ArrayList<>();
    private Map<String, Object> suppressionInfo;

    public ValidationResponse() {
    }

    // ── Tespit bilgileri getter/setter ──

    public String getDetectedDocumentType() {
        return detectedDocumentType;
    }

    public void setDetectedDocumentType(String detectedDocumentType) {
        this.detectedDocumentType = detectedDocumentType;
    }

    public String getAppliedXsd() {
        return appliedXsd;
    }

    public void setAppliedXsd(String appliedXsd) {
        this.appliedXsd = appliedXsd;
    }

    public String getAppliedSchematron() {
        return appliedSchematron;
    }

    public void setAppliedSchematron(String appliedSchematron) {
        this.appliedSchematron = appliedSchematron;
    }

    public String getAppliedXsdPath() {
        return appliedXsdPath;
    }

    public void setAppliedXsdPath(String appliedXsdPath) {
        this.appliedXsdPath = appliedXsdPath;
    }

    public String getAppliedSchematronPath() {
        return appliedSchematronPath;
    }

    public void setAppliedSchematronPath(String appliedSchematronPath) {
        this.appliedSchematronPath = appliedSchematronPath;
    }

    // ── Doğrulama sonuçları getter/setter ──

    public boolean isValidSchema() {
        return validSchema;
    }

    public void setValidSchema(boolean validSchema) {
        this.validSchema = validSchema;
    }

    public boolean isValidSchematron() {
        return validSchematron;
    }

    public void setValidSchematron(boolean validSchematron) {
        this.validSchematron = validSchematron;
    }

    public List<String> getSchemaValidationErrors() {
        return schemaValidationErrors;
    }

    public void setSchemaValidationErrors(List<String> schemaValidationErrors) {
        this.schemaValidationErrors = schemaValidationErrors;
    }

    public List<SchematronError> getSchematronValidationErrors() {
        return schematronValidationErrors;
    }

    public void setSchematronValidationErrors(List<SchematronError> schematronValidationErrors) {
        this.schematronValidationErrors = schematronValidationErrors;
    }

    /**
     * Bastırma (suppression) detayları.
     * <p>
     * Profil uygulanmadığında {@code null}, uygulandığında:
     * <pre>
     * {
     *   "profile": "unsigned",
     *   "totalRawErrors": 4,
     *   "suppressedCount": 3,
     *   "suppressedErrors": [...]
     * }
     * </pre>
     */
    public Map<String, Object> getSuppressionInfo() {
        return suppressionInfo;
    }

    public void setSuppressionInfo(Map<String, Object> suppressionInfo) {
        this.suppressionInfo = suppressionInfo;
    }
}
