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
     * Doğrulanacak XML belge içeriği (byte dizisi).
     */
    private byte[] source;

    public ValidationRequest() {
    }

    public ValidationRequest(SchemaValidationType schemaValidationType,
                             SchematronValidationType schematronValidationType,
                             byte[] source) {
        this.schemaValidationType = schemaValidationType;
        this.schematronValidationType = schematronValidationType;
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

    public byte[] getSource() {
        return source;
    }

    public void setSource(byte[] source) {
        this.source = source;
    }
}
