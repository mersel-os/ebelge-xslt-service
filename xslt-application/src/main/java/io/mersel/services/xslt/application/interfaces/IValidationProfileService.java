package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.models.SchematronCustomAssertion;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.SuppressionResult;
import io.mersel.services.xslt.application.models.ValidationProfile;
import io.mersel.services.xslt.application.models.XsdOverride;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Doğrulama profil servisi arayüzü.
 * <p>
 * Profillerin yönetimi ve doğrulama sonuçlarına bastırma kurallarının
 * uygulanmasını sağlar.
 * <p>
 * Bastırma kuralları kapsam (scope) destekler: her kural opsiyonel olarak
 * belirli belge tiplerine (örn: INVOICE, DESPATCH_ADVICE) veya schematron
 * tiplerine (örn: UBLTR_MAIN, EDEFTER_KEBIR) kısıtlanabilir.
 */
public interface IValidationProfileService {

    /**
     * Belirtilen isimdeki profili döndürür.
     *
     * @param profileName Profil adı (örn: "unsigned", "my-company")
     * @return Profil, bulunamazsa {@link Optional#empty()}
     */
    Optional<ValidationProfile> getProfile(String profileName);

    /**
     * Mevcut tüm profilleri döndürür.
     *
     * @return Profil adı → profil eşleşmesi
     */
    Map<String, ValidationProfile> getAvailableProfiles();

    /**
     * Schematron hatalarına profil tabanlı bastırma uygular.
     * <p>
     * Profil ve/veya ek bastırma kuralları belirtilmişse, ham hatalar
     * filtrelenir ve bastırılmış hatalar ayrıca raporlanır.
     * <p>
     * {@code activeTypes} parametresi, mevcut doğrulama isteğindeki belge
     * tiplerini içerir. Scope'lu kurallar yalnızca bu tipler eşleştiğinde uygulanır.
     *
     * @param rawErrors              Ham Schematron hataları
     * @param profileName            Uygulanacak profil adı ({@code null} ise profil uygulanmaz)
     * @param additionalSuppressions Ek bastırma kuralları — ruleId olarak işlenir
     * @param activeTypes            Mevcut doğrulama tiplerinin kümeleri (örn: {"INVOICE", "UBLTR_MAIN"})
     * @return Bastırma sonucu (aktif + bastırılmış hatalar)
     */
    SuppressionResult applySchematronSuppressions(
            List<SchematronError> rawErrors,
            String profileName,
            List<String> additionalSuppressions,
            Set<String> activeTypes
    );

    /**
     * XSD hatalarına metin tabanlı bastırma uygular.
     * <p>
     * XSD hataları yapılandırılmış metadata içermediği için
     * sadece metin eşleşmesi ({@code text} modu) ile bastırılabilir.
     *
     * @param rawErrors              Ham XSD hataları
     * @param profileName            Uygulanacak profil adı ({@code null} ise profil uygulanmaz)
     * @param additionalSuppressions Ek bastırma kuralları — metin olarak işlenir
     * @param activeTypes            Mevcut doğrulama tiplerinin kümeleri (örn: {"INVOICE"})
     * @return Bastırılmamış (aktif) hatalar
     */
    List<String> applyXsdSuppressions(
            List<String> rawErrors,
            String profileName,
            List<String> additionalSuppressions,
            Set<String> activeTypes
    );

    /**
     * Belirtilen profil ve şema tipi için XSD override kurallarını çözümler.
     * <p>
     * Profil kalıtım zinciri (extends) de dahil olmak üzere tüm override'lar
     * birleştirilir. Alt profildeki override'lar üst profildeki aynı element'in
     * override'ını ezer (override).
     *
     * @param profileName Profil adı ({@code null} ise boş liste döner)
     * @param schemaType  Şema tipi adı (örn: "INVOICE", "DESPATCH_ADVICE")
     * @return Çözümlenmiş XSD override listesi (boş liste = override yok)
     */
    List<XsdOverride> resolveXsdOverrides(String profileName, String schemaType);

    /**
     * Belirtilen profil ve Schematron tipi için özel Schematron kurallarını çözümler.
     * <p>
     * Profil kalıtım zinciri (extends) de dahil olmak üzere tüm kurallar
     * birleştirilir. Alt profildeki kurallar üst profildeki kurallara eklenir.
     *
     * @param profileName    Profil adı ({@code null} ise boş liste döner)
     * @param schematronType Schematron tipi adı (örn: "UBLTR_MAIN", "EDEFTER_YEVMIYE")
     * @return Çözümlenmiş özel Schematron kuralları listesi (boş liste = kural yok)
     */
    List<SchematronCustomAssertion> resolveSchematronRules(String profileName, String schematronType);

    /**
     * Profili kaydeder (yeni oluşturur veya mevcudu günceller).
     * <p>
     * YAML dosyasına yazar ve profilleri yeniden yükler.
     *
     * @param profile Kaydedilecek profil
     * @throws IOException YAML dosyasına yazma başarısız olursa
     */
    void saveProfile(ValidationProfile profile) throws IOException;

    /**
     * Belirtilen profili siler.
     * <p>
     * YAML dosyasından kaldırır ve profilleri yeniden yükler.
     *
     * @param profileName Silinecek profil adı
     * @return {@code true} profil bulunup silindiyse, {@code false} bulunamadıysa
     * @throws IOException YAML dosyasına yazma başarısız olursa
     */
    boolean deleteProfile(String profileName) throws IOException;

    // ── Global Schematron Kuralları ──────────────────────────────────

    /**
     * Global özel Schematron kurallarını döndürür.
     * <p>
     * Bu kurallar profil bağımsızdır ve her doğrulama isteğinde otomatik olarak
     * aktiftir. YAML dosyasının top-level {@code schematron-rules:} bölümünden okunur.
     *
     * @return Schematron tipi adı → kural listesi eşleşmesi (örn: "UBLTR_MAIN" → [...])
     */
    Map<String, List<SchematronCustomAssertion>> getGlobalSchematronRules();

    /**
     * Global özel Schematron kurallarını kaydeder.
     * <p>
     * YAML dosyasının top-level {@code schematron-rules:} bölümüne yazar,
     * mevcut tüm global kuralları değiştirir ve reload tetikler.
     * Reload sonrasında yeni kurallar otomatik olarak derlenir.
     *
     * @param rules Schematron tipi adı → kural listesi eşleşmesi
     * @throws IOException YAML dosyasına yazma başarısız olursa
     */
    void saveGlobalSchematronRules(Map<String, List<SchematronCustomAssertion>> rules) throws IOException;
}
