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
    // GIB, eArsivRaporu kökünü her ürün paketinde farklı bir eArsiv.xsd ile yayınlıyor.
    // Şemalar aynı namespace'te aynı kök elementi farklı içerik modelleriyle tanımladığı
    // için birleştirilemezler; rapor ailesi başına ayrı bir tür gerekir.
    EARCHIVE_REPORT,
    EARCHIVE_REPORT_EADISYON,
    EARCHIVE_REPORT_EDOVIZ,
    EARCHIVE_REPORT_EDEKONT,
    EARCHIVE_REPORT_EGIDER_PUSULASI,
    // Genel e-Arşiv paketindeki eArsivVeri.xsd: aynı namespace'te ayrı bir kök (eArsivVeri),
    // serbest meslek makbuzu verisini taşır. EArsiv.xsd ile birleştirilemez (aynı tip adları).
    EARCHIVE_DATA,

    // ── e-Defter ──
    EDEFTER_YEVMIYE,
    EDEFTER_KEBIR,
    EDEFTER_BERAT,
    EDEFTER_RAPOR,

    // ── e-Envanter ──
    ENVANTER_DEFTER,
    ENVANTER_BERAT
}
