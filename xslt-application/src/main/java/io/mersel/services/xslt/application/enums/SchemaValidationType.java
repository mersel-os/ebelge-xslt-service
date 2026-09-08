package io.mersel.services.xslt.application.enums;

/**
 * XML Schema (XSD) doğrulama için desteklenen belge tipleri.
 * <p>
 * {@code EARCHIVE_REPORT*} tipleri e-Arşiv faturasını değil {@code eArsivRaporu}
 * belgesini doğrular; adlandırma {@link DocumentType} ve
 * {@link SchematronValidationType} ile birebir aynıdır.
 */
public enum SchemaValidationType {
    INVOICE,
    DESPATCH_ADVICE,
    RECEIPT_ADVICE,
    CREDIT_NOTE,
    APPLICATION_RESPONSE,
    EARCHIVE_REPORT,
    EARCHIVE_REPORT_EDOVIZ,
    EARCHIVE_REPORT_EDEKONT,
    EARCHIVE_REPORT_EGIDER_PUSULASI,
    EDEFTER
}
