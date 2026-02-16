package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.models.SchematronError;

import java.util.List;

/**
 * Schematron doğrulama servisi arayüzü.
 * <p>
 * XML belgelerini Schematron kurallarına (önceden derlenmiş XSLT) göre doğrular.
 * Sonuçlar yapılandırılmış {@link SchematronError} nesneleri olarak döner,
 * profil tabanlı bastırma (suppression) desteği sağlar.
 */
public interface ISchematronValidator {

    /**
     * XML belgesini belirtilen Schematron tipine göre doğrular.
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
}
