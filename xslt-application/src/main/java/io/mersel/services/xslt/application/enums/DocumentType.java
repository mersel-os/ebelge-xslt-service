package io.mersel.services.xslt.application.enums;

/**
 * Otomatik tespit edilen XML belge türleri.
 * <p>
 * Her belge türü, ilgili XSD şema doğrulama tipi ({@link SchemaValidationType})
 * ve Schematron doğrulama tipi ({@link SchematronValidationType}) ile eşleştirilir.
 */
public enum DocumentType {

    // ── UBL-TR Belge Türleri ──
    INVOICE,
    CREDIT_NOTE,
    DESPATCH_ADVICE,
    RECEIPT_ADVICE,
    APPLICATION_RESPONSE,

    // ── e-Arşiv ──
    EARCHIVE_REPORT,

    // ── e-Defter ──
    EDEFTER_YEVMIYE,
    EDEFTER_KEBIR,
    EDEFTER_BERAT,
    EDEFTER_RAPOR,

    // ── e-Envanter ──
    ENVANTER_DEFTER,
    ENVANTER_BERAT
}
