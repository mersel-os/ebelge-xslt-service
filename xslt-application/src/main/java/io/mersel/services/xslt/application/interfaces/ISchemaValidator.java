package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.models.XsdOverride;

import java.util.List;

/**
 * XML Schema (XSD) doğrulama servisi arayüzü.
 * <p>
 * XML belgelerini ilgili XSD şemalarına göre doğrular.
 * Opsiyonel olarak profil bazlı XSD override'ları destekler.
 */
public interface ISchemaValidator {

    /**
     * XML belgesini belirtilen şema tipine göre doğrular.
     *
     * @param source       Doğrulanacak XML içeriği
     * @param schemaType   Şema doğrulama tipi (Invoice, DespatchAdvice vb.)
     * @return Doğrulama hataları listesi (boş liste = geçerli)
     */
    List<String> validate(byte[] source, SchemaValidationType schemaType);

    /**
     * XML belgesini belirtilen şema tipine göre, XSD override'ları uygulayarak doğrular.
     * <p>
     * Override'lar orijinal XSD'deki {@code minOccurs} / {@code maxOccurs} değerlerini
     * değiştirir. Bu sayede GIB orijinal XSD dosyaları değiştirilmeden, profil bazlı
     * esneklik sağlanır.
     *
     * @param source       Doğrulanacak XML içeriği
     * @param schemaType   Şema doğrulama tipi (Invoice, DespatchAdvice vb.)
     * @param overrides    Uygulanacak XSD override listesi (boş ise orijinal şema kullanılır)
     * @return Doğrulama hataları listesi (boş liste = geçerli)
     */
    List<String> validate(byte[] source, SchemaValidationType schemaType, List<XsdOverride> overrides);

    /**
     * XML belgesini belirtilen şema tipine göre, profil bazlı XSD override'ları uygulayarak doğrular.
     * <p>
     * {@code profileName} parametresi override cache key'inde ve auto-generated XSD
     * dosya adında kullanılır. Her profilin override'ı bağımsız olarak derlenir ve cache'lenir.
     *
     * @param source       Doğrulanacak XML içeriği
     * @param schemaType   Şema doğrulama tipi (Invoice, DespatchAdvice vb.)
     * @param overrides    Uygulanacak XSD override listesi (boş ise orijinal şema kullanılır)
     * @param profileName  Override'ları talep eden profil adı (cache key ve dosya adı için)
     * @return Doğrulama hataları listesi (boş liste = geçerli)
     */
    List<String> validate(byte[] source, SchemaValidationType schemaType, List<XsdOverride> overrides, String profileName);

    /**
     * Override XSD cache'ini temizler.
     * <p>
     * Profil değişikliklerinde (kaydet/sil) çağrılmalıdır.
     * Base şemalar etkilenmez, yalnızca override'lı derlenmiş şemalar temizlenir.
     */
    void invalidateOverrideCache();

    /**
     * Override'lı XSD şemasını önceden derler, cache'e yazar ve auto-generated dosyasını oluşturur.
     * <p>
     * Profil kaydedildiğinde çağrılır — validation isteği beklenmeden override şeması
     * hemen derlenir ve {@code auto-generated/schema-overrides/} dizinine yazılır.
     *
     * @param schemaType  Şema doğrulama tipi (Invoice, DespatchAdvice vb.)
     * @param overrides   Uygulanacak XSD override listesi
     * @param profileName Profil adı (cache key ve dosya adı için)
     */
    void precompileOverrides(SchemaValidationType schemaType, List<XsdOverride> overrides, String profileName);
}
