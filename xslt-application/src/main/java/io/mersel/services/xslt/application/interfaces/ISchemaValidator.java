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
}
