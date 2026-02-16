package io.mersel.services.xslt.application.models;

import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.enums.SchematronValidationType;

/**
 * XML doğrulama isteği modeli.
 * <p>
 * Schema (XSD) ve Schematron doğrulaması için gerekli tüm bilgileri taşır.
 */
public class ValidationRequest {

    private SchemaValidationType schemaValidationType;
    private SchematronValidationType schematronValidationType;

    /**
     * UBL-TR Main Schematron tipi (örn: "efatura", "earchive").
     * Sadece {@link SchematronValidationType#UBLTR_MAIN} için kullanılır.
     */
    private String ublTrMainSchematronType;

    /**
     * Doğrulanacak XML belge içeriği (byte dizisi).
     */
    private byte[] source;

    public ValidationRequest() {
    }

    public ValidationRequest(SchemaValidationType schemaValidationType,
                             SchematronValidationType schematronValidationType,
                             String ublTrMainSchematronType,
                             byte[] source) {
        this.schemaValidationType = schemaValidationType;
        this.schematronValidationType = schematronValidationType;
        this.ublTrMainSchematronType = ublTrMainSchematronType;
        this.source = source;
    }

    public SchemaValidationType getSchemaValidationType() {
        return schemaValidationType;
    }

    public void setSchemaValidationType(SchemaValidationType schemaValidationType) {
        this.schemaValidationType = schemaValidationType;
    }

    public SchematronValidationType getSchematronValidationType() {
        return schematronValidationType;
    }

    public void setSchematronValidationType(SchematronValidationType schematronValidationType) {
        this.schematronValidationType = schematronValidationType;
    }

    public String getUblTrMainSchematronType() {
        return ublTrMainSchematronType;
    }

    public void setUblTrMainSchematronType(String ublTrMainSchematronType) {
        this.ublTrMainSchematronType = ublTrMainSchematronType;
    }

    public byte[] getSource() {
        return source;
    }

    public void setSource(byte[] source) {
        this.source = source;
    }
}
