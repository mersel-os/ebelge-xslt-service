package io.mersel.services.xslt.application.models;

/**
 * Yapılandırılmış Schematron doğrulama hatası.
 * <p>
 * Schematron pipeline'ından gelen hata mesajlarını,
 * soyut kural kimliği ({@code ruleId}) ve XPath test ifadesi ({@code test})
 * ile birlikte taşır. Bu metadata, profil tabanlı bastırma (suppression)
 * mekanizması tarafından kullanılır.
 *
 * @param ruleId  Soyut kural kimliği (örn: "InvoiceIDCheck", "XadesSignatureCheck").
 *                Runtime'da derlenen Schematron'larda dolu, pre-compiled XSL'lerde {@code null} olabilir.
 * @param test    XPath test ifadesi (örn: "matches(cbc:ID,'^[A-Z0-9]{3}20...')").
 *                Runtime'da derlenen Schematron'larda dolu, pre-compiled XSL'lerde {@code null} olabilir.
 * @param message Hata mesajı metni (her zaman dolu).
 */
public record SchematronError(
        String ruleId,
        String test,
        String message
) {

    /**
     * Kullanıcıya gösterilecek hata metnini döndürür.
     * <p>
     * Geriye dönük uyumluluk ve basit gösterim için sadece mesajı döner.
     */
    public String toDisplayString() {
        return message;
    }
}
