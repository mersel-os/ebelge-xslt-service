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
     * XML belgesini belirtilen Schematron tipine göre doğrular.
     * <p>
     * Global özel kurallar varsa, bunlar zaten {@code compiledSchematrons} cache'inde
     * derlenmiş olarak bulunur ve otomatik olarak uygulanır.
     *
     * @param source                   Doğrulanacak XML içeriği
     * @param schematronType           Schematron doğrulama tipi
     * @param ublTrMainSchematronType  UBL-TR Main Schematron alt tipi (örn: "efatura", "earchive")
     *                                 Sadece {@link SchematronValidationType#UBLTR_MAIN} için geçerlidir.
     * @param sourceFileName           Kaynak XML dosya adı. e-Defter Schematron kuralları {@code base-uri()}
     *                                 fonksiyonu ile dosya adını kontrol eder (VKN/TCKN eşleştirmesi vb.).
     *                                 {@code null} ise dosya adı bilgisi gönderilmez.
     * @return Yapılandırılmış doğrulama hataları listesi (boş liste = geçerli)
     */
    List<SchematronError> validate(byte[] source, SchematronValidationType schematronType,
                                   String ublTrMainSchematronType, String sourceFileName);

    /**
     * XML belgesini belirtilen Schematron tipine göre, profil bazlı özel kurallar enjekte ederek doğrular.
     * <p>
     * Profil kuralları global kurallarla birleştirilir: orijinal Schematron XML'e
     * hem global hem profil kuralları {@code <sch:pattern>} bloğu olarak eklenir
     * ve ISO Schematron pipeline ile birlikte derlenir. Derlenen sonuç cache'lenir.
     *
     * @param source                   Doğrulanacak XML içeriği
     * @param schematronType           Schematron doğrulama tipi
     * @param ublTrMainSchematronType  UBL-TR Main Schematron alt tipi (örn: "efatura", "earchive")
     * @param sourceFileName           Kaynak XML dosya adı ({@code null} ise dosya adı bilgisi gönderilmez)
     * @param customRules              Enjekte edilecek profil bazlı özel Schematron kuralları (boş ise standart doğrulama)
     * @param profileName              Kuralları talep eden profil adı (cache key ve dosya adı için)
     * @return Yapılandırılmış doğrulama hataları listesi (boş liste = geçerli)
     */
    List<SchematronError> validate(byte[] source, SchematronValidationType schematronType,
                                   String ublTrMainSchematronType, String sourceFileName,
                                   List<SchematronCustomAssertion> customRules, String profileName);

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
