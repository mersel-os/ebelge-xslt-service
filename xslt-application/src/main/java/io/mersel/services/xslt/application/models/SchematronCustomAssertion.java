package io.mersel.services.xslt.application.models;

/**
 * Tek bir özel Schematron assertion tanımı.
 * <p>
 * Profil bazlı olarak UBL-TR Main Schematron gibi kaynak Schematron dosyalarına
 * ek doğrulama kuralları eklenmesini sağlar. Kurallar, ISO Schematron pipeline'ına
 * girmeden önce orijinal Schematron XML'e {@code <sch:pattern>} bloğu olarak enjekte edilir.
 * <p>
 * Aynı {@code context} değerine sahip assertion'lar tek bir {@code <sch:rule>} altında
 * gruplanır. Farklı pattern'daki kurallar birbirinden bağımsız çalışır (ISO Schematron spesifikasyonu).
 *
 * @param context XPath context ifadesi — kuralın hangi XML node'unda çalışacağını belirler
 *                (örn: "inv:Invoice", "inv:Invoice/cac:InvoiceLine")
 * @param test    XPath test ifadesi — true olmalıdır, aksi halde hata üretilir
 *                (örn: "cac:AccountingSupplierParty/cac:Party/cac:PostalAddress")
 * @param message Hata mesajı — test başarısız olduğunda gösterilecek metin
 * @param id      İsteğe bağlı kural kimliği — bastırma (suppression) referansı için
 *                (örn: "CUSTOM-SUPPLIER-ADDRESS"). {@code null} ise otomatik atanmaz.
 */
public record SchematronCustomAssertion(
        String context,
        String test,
        String message,
        String id
) {
}
