package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.models.SchematronCustomAssertion;
import io.mersel.services.xslt.application.models.SchematronError;

import java.util.List;
import java.util.Map;

/**
 * Schematron doğrulama servisi arayüzü.
 * <p>
 * XML belgelerini Schematron kurallarına (önceden derlenmiş XSLT) göre doğrular.
 * Sonuçlar yapılandırılmış {@link SchematronError} nesneleri olarak döner,
 * profil tabanlı bastırma (suppression) desteği sağlar.
 * <p>
 * İki katmanlı özel Schematron kuralları desteği:
 * <ul>
 *   <li><b>Global kurallar</b> — profil bağımsız, her zaman aktif. {@code reload()} sırasında
 *       orijinal Schematron XML'e enjekte edilir ve {@code compiledSchematrons} cache'ine derlenir.</li>
 *   <li><b>Profil kuralları</b> — profil seçildiğinde global kurallar üzerine eklenir.
 *       On-demand olarak derlenir ve {@code customRuleCache}'te tutulur.</li>
 * </ul>
 */
public interface ISchematronValidator {

    /**
     * XML belgesini belirtilen Schematron tipine göre, profil bazlı özel kurallar ve
     * ek XSLT parametreleri ile doğrular.
     * <p>
     * {@code parameters} ile Schematron XSLT'sine {@code xsl:param} değerleri geçirilebilir.
     * Özel şematron kurallarında {@code $parametre_adi} şeklinde tanımlanan değişkenler bu
     * parametreler ile doldurulur. UBL-TR Main Schematron alt tipi {@code parameters} map'inde
     * {@code "type"} anahtarı ile gönderilmelidir (örn: {@code Map.of("type", "TEMELFATURA")}).
     *
     * @param source                   Doğrulanacak XML içeriği
     * @param schematronType           Schematron doğrulama tipi
     * @param sourceFileName           Kaynak XML dosya adı ({@code null} ise dosya adı bilgisi gönderilmez)
     * @param customRules              Enjekte edilecek profil bazlı özel Schematron kuralları (boş ise standart doğrulama)
     * @param profileName              Kuralları talep eden profil adı (cache key ve dosya adı için)
     * @param parameters               Schematron XSLT parametreleri (key/value). Boş veya {@code null} ise ek parametre gönderilmez.
     * @return Yapılandırılmış doğrulama hataları listesi (boş liste = geçerli)
     */
    List<SchematronError> validate(byte[] source, SchematronValidationType schematronType,
                                   String sourceFileName,
                                   List<SchematronCustomAssertion> customRules, String profileName,
                                   Map<String, String> parameters);

    /**
     * Özel kural cache'ini temizler.
     * <p>
     * Profil veya global kural değişikliklerinde çağrılmalıdır.
     * Base Schematron'lar etkilenmez, yalnızca özel kurallarla derlenmiş Schematron'lar temizlenir.
     */
    void invalidateCustomRuleCache();

    /**
     * Özel kurallarla Schematron'u önceden derler, cache'e yazar ve auto-generated dosyasını oluşturur.
     * <p>
     * Profil kaydedildiğinde çağrılır — validation isteği beklenmeden özel kurallar
     * hemen derlenir ve {@code auto-generated/schematron-rules/} dizinine yazılır.
     *
     * @param schematronType Schematron doğrulama tipi (UBLTR_MAIN, EDEFTER_YEVMIYE vb.)
     * @param customRules    Enjekte edilecek özel kurallar
     * @param profileName    Profil adı (cache key ve dosya adı için)
     */
    void precompileCustomRules(SchematronValidationType schematronType,
                               List<SchematronCustomAssertion> customRules, String profileName);

    // ── Global Kurallar ──────────────────────────────────────────────

    /**
     * Global özel Schematron kurallarını ayarlar.
     * <p>
     * Bu kurallar {@code reload()} sırasında orijinal Schematron XML'e enjekte edilir
     * ve {@code compiledSchematrons} cache'ine derlenir. Profil seçilsin seçilmesin
     * her doğrulama isteğinde otomatik olarak aktiftir.
     * <p>
     * {@code ValidationProfileRegistry.reload()} tarafından YAML'dan okunan global
     * kurallarla çağrılır.
     *
     * @param rules Schematron tipi → kural listesi eşleşmesi
     */
    void setGlobalCustomRules(Map<SchematronValidationType, List<SchematronCustomAssertion>> rules);

    /**
     * Mevcut global özel Schematron kurallarını döndürür.
     *
     * @return Schematron tipi → kural listesi eşleşmesi (değiştirilemez)
     */
    Map<SchematronValidationType, List<SchematronCustomAssertion>> getGlobalCustomRules();
}
