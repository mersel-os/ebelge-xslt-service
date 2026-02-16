package io.mersel.services.xslt.application.models;

import io.mersel.services.xslt.application.enums.DocumentType;
import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.enums.SchematronValidationType;

import java.util.Map;

/**
 * Belge türünden XSD şema tipi ve Schematron doğrulama tipine eşleme sabitleri.
 * <p>
 * Otomatik tespit edilen {@link DocumentType} değeri kullanılarak
 * doğru XSD ve Schematron doğrulama tiplerini çözümler.
 */
public final class DocumentTypeMapping {

    private DocumentTypeMapping() {
        // Utility class — instantiation engellenir
    }

    /**
     * DocumentType → SchemaValidationType eşleme tablosu.
     */
    public static final Map<DocumentType, SchemaValidationType> SCHEMA_MAP = Map.ofEntries(
            Map.entry(DocumentType.INVOICE, SchemaValidationType.INVOICE),
            Map.entry(DocumentType.CREDIT_NOTE, SchemaValidationType.CREDIT_NOTE),
            Map.entry(DocumentType.DESPATCH_ADVICE, SchemaValidationType.DESPATCH_ADVICE),
            Map.entry(DocumentType.RECEIPT_ADVICE, SchemaValidationType.RECEIPT_ADVICE),
            Map.entry(DocumentType.APPLICATION_RESPONSE, SchemaValidationType.APPLICATION_RESPONSE),
            Map.entry(DocumentType.EARCHIVE_REPORT, SchemaValidationType.EARCHIVE),
            Map.entry(DocumentType.EDEFTER_YEVMIYE, SchemaValidationType.EDEFTER),
            Map.entry(DocumentType.EDEFTER_KEBIR, SchemaValidationType.EDEFTER),
            Map.entry(DocumentType.EDEFTER_BERAT, SchemaValidationType.EDEFTER),
            Map.entry(DocumentType.EDEFTER_RAPOR, SchemaValidationType.EDEFTER),
            Map.entry(DocumentType.ENVANTER_DEFTER, SchemaValidationType.EDEFTER),
            Map.entry(DocumentType.ENVANTER_BERAT, SchemaValidationType.EDEFTER)
    );

    /**
     * DocumentType → SchematronValidationType eşleme tablosu.
     */
    public static final Map<DocumentType, SchematronValidationType> SCHEMATRON_MAP = Map.ofEntries(
            Map.entry(DocumentType.INVOICE, SchematronValidationType.UBLTR_MAIN),
            Map.entry(DocumentType.CREDIT_NOTE, SchematronValidationType.UBLTR_MAIN),
            Map.entry(DocumentType.DESPATCH_ADVICE, SchematronValidationType.UBLTR_MAIN),
            Map.entry(DocumentType.RECEIPT_ADVICE, SchematronValidationType.UBLTR_MAIN),
            Map.entry(DocumentType.APPLICATION_RESPONSE, SchematronValidationType.UBLTR_MAIN),
            Map.entry(DocumentType.EARCHIVE_REPORT, SchematronValidationType.EARCHIVE_REPORT),
            Map.entry(DocumentType.EDEFTER_YEVMIYE, SchematronValidationType.EDEFTER_YEVMIYE),
            Map.entry(DocumentType.EDEFTER_KEBIR, SchematronValidationType.EDEFTER_KEBIR),
            Map.entry(DocumentType.EDEFTER_BERAT, SchematronValidationType.EDEFTER_BERAT),
            Map.entry(DocumentType.EDEFTER_RAPOR, SchematronValidationType.EDEFTER_RAPOR),
            Map.entry(DocumentType.ENVANTER_DEFTER, SchematronValidationType.ENVANTER_DEFTER),
            Map.entry(DocumentType.ENVANTER_BERAT, SchematronValidationType.ENVANTER_BERAT)
    );

    /**
     * DocumentType → XSD dosya yolu eşleme tablosu.
     * Kullanıcıya response'da gösterilmek üzere.
     */
    public static final Map<DocumentType, String> XSD_PATH_MAP = Map.ofEntries(
            Map.entry(DocumentType.INVOICE, "validator/ubl-tr-package/schema/maindoc/UBL-Invoice-2.1.xsd"),
            Map.entry(DocumentType.CREDIT_NOTE, "validator/ubl-tr-package/schema/maindoc/UBL-CreditNote-2.1.xsd"),
            Map.entry(DocumentType.DESPATCH_ADVICE, "validator/ubl-tr-package/schema/maindoc/UBL-DespatchAdvice-2.1.xsd"),
            Map.entry(DocumentType.RECEIPT_ADVICE, "validator/ubl-tr-package/schema/maindoc/UBL-ReceiptAdvice-2.1.xsd"),
            Map.entry(DocumentType.APPLICATION_RESPONSE, "validator/ubl-tr-package/schema/maindoc/UBL-ApplicationResponse-2.1.xsd"),
            Map.entry(DocumentType.EARCHIVE_REPORT, "validator/earchive/schema/EArsiv.xsd"),
            Map.entry(DocumentType.EDEFTER_YEVMIYE, "validator/eledger/schema/edefter.xsd"),
            Map.entry(DocumentType.EDEFTER_KEBIR, "validator/eledger/schema/edefter.xsd"),
            Map.entry(DocumentType.EDEFTER_BERAT, "validator/eledger/schema/edefter.xsd"),
            Map.entry(DocumentType.EDEFTER_RAPOR, "validator/eledger/schema/edefter.xsd"),
            Map.entry(DocumentType.ENVANTER_DEFTER, "validator/eledger/schema/edefter.xsd"),
            Map.entry(DocumentType.ENVANTER_BERAT, "validator/eledger/schema/edefter.xsd")
    );

    /**
     * SchematronValidationType → Schematron dosya yolu eşleme tablosu.
     * Kullanıcıya response'da gösterilmek üzere.
     */
    public static final Map<SchematronValidationType, String> SCHEMATRON_PATH_MAP = Map.ofEntries(
            Map.entry(SchematronValidationType.UBLTR_MAIN, "validator/ubl-tr-package/schematron/UBL-TR_Main_Schematron.xml"),
            Map.entry(SchematronValidationType.EARCHIVE_REPORT, "validator/earchive/schematron/earsiv_schematron.xsl"),
            Map.entry(SchematronValidationType.EDEFTER_YEVMIYE, "validator/eledger/schematron/edefter_yevmiye.sch"),
            Map.entry(SchematronValidationType.EDEFTER_KEBIR, "validator/eledger/schematron/edefter_kebir.sch"),
            Map.entry(SchematronValidationType.EDEFTER_BERAT, "validator/eledger/schematron/edefter_berat.sch"),
            Map.entry(SchematronValidationType.EDEFTER_RAPOR, "validator/eledger/schematron/edefter_rapor.sch"),
            Map.entry(SchematronValidationType.ENVANTER_DEFTER, "validator/eledger/schematron/envanter_defter.sch"),
            Map.entry(SchematronValidationType.ENVANTER_BERAT, "validator/eledger/schematron/envanter_berat.sch")
    );

    /**
     * Tespit edilen belge türü için XSD dosya adını döndürür.
     */
    public static String getXsdFileName(DocumentType documentType) {
        String path = XSD_PATH_MAP.get(documentType);
        if (path == null) return null;
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /**
     * Tespit edilen belge türü için Schematron dosya adını döndürür.
     */
    public static String getSchematronFileName(DocumentType documentType) {
        SchematronValidationType schematronType = SCHEMATRON_MAP.get(documentType);
        if (schematronType == null) return null;
        String path = SCHEMATRON_PATH_MAP.get(schematronType);
        if (path == null) return null;
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
