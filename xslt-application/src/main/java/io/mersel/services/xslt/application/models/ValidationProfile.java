package io.mersel.services.xslt.application.models;

import java.util.List;
import java.util.Map;

/**
 * Doğrulama profili tanımı.
 * <p>
 * YAML konfigürasyonundan yüklenir ve belirli Schematron/XSD hatalarının
 * bastırılması (suppression) için kuralları içerir.
 * <p>
 * Profiller {@code extends} ile kalıtım destekler:
 * alt profil, üst profilin tüm bastırma kurallarını miras alır
 * ve kendi kurallarını ekler.
 * <p>
 * {@code xsdOverrides} ile GIB orijinal XSD dosyalarındaki element kısıtlamaları
 * (minOccurs/maxOccurs) profil bazlı olarak değiştirilebilir.
 * Map key'i {@code SchemaValidationType} adıdır (INVOICE, DESPATCH_ADVICE, ...).
 *
 * @param name           Profil adı (örn: "unsigned", "my-company")
 * @param description    Profil açıklaması
 * @param extendsProfile Miras alınan profil adı (isteğe bağlı, {@code null} ise kalıtım yok)
 * @param suppressions   Bastırma kuralları listesi
 * @param xsdOverrides   XSD override kuralları — key: SchemaValidationType adı, value: override listesi
 */
public record ValidationProfile(
        String name,
        String description,
        String extendsProfile,
        List<SuppressionRule> suppressions,
        Map<String, List<XsdOverride>> xsdOverrides
) {

    /**
     * Tek bir bastırma kuralı.
     * <p>
     * Üç eşleşme modu desteklenir:
     * <ul>
     *   <li>{@code ruleId} — Soyut kural kimliğine göre (en kararlı, tercih edilen)</li>
     *   <li>{@code test} — XPath test ifadesine göre</li>
     *   <li>{@code text} — Hata mesajı metnine göre (pre-compiled XSL'ler için fallback)</li>
     * </ul>
     * <p>
     * {@code pattern} alanı Java regex destekler (örn: ".*Signature.*").
     * <p>
     * {@code scope} alanı isteğe bağlıdır. Belirtilmezse kural tüm belge tiplerine
     * uygulanır (geriye uyumlu). Belirtilirse sadece listedeki tipler için geçerlidir.
     * Hem {@code SchemaValidationType} (INVOICE, DESPATCH_ADVICE, ...) hem
     * {@code SchematronValidationType} (UBLTR_MAIN, EDEFTER_KEBIR, ...) değerleri kabul edilir.
     *
     * @param match       Eşleşme modu: "ruleId", "test" veya "text"
     * @param pattern     Regex pattern
     * @param scope       Kapsam: bu kuralın uygulanacağı belge tipleri (boş ise tümüne uygulanır)
     * @param description İsteğe bağlı açıklama
     */
    public record SuppressionRule(
            String match,
            String pattern,
            List<String> scope,
            String description
    ) {
    }
}
